package com.clientflow.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.common.PageResponse;
import com.clientflow.common.Text;
import com.clientflow.common.Versions;
import com.clientflow.config.AppProperties;
import com.clientflow.deal.RecordLinks;
import com.clientflow.deal.RecordLinks.Links;
import com.clientflow.task.TaskDtos.TaskRequest;
import com.clientflow.task.TaskDtos.TaskResponse;
import com.clientflow.user.AppUser;
import com.clientflow.user.CurrentUserService;

@Service
public class TaskService {

    /**
     * Due-date windows for the task list. "Today" is the calendar day in the business time zone,
     * so a task due today is not overdue until midnight in Riyadh, whatever the server's clock zone.
     * نطاقات تاريخ الاستحقاق لقائمة المهام. "اليوم" هو اليوم بتوقيت النشاط،
     * فالمهمة المستحقة اليوم ما تصير متأخرة إلا بعد منتصف الليل بتوقيت الرياض، أيًا كانت منطقة الخادم.
     */
    public enum Due { OVERDUE, TODAY, WEEK }

    private final TaskRepository tasks;
    private final RecordLinks links;
    private final CurrentUserService currentUser;
    private final AppProperties properties;

    public TaskService(TaskRepository tasks, RecordLinks links, CurrentUserService currentUser,
                       AppProperties properties) {
        this.tasks = tasks;
        this.links = links;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    public LocalDate today() {
        return LocalDate.now(properties.timeZone());
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> search(String q, Long assigneeId, TaskStatus status, Due due, Long companyId,
                                             Long dealId, Pageable pageable) {
        LocalDate today = today();
        String pattern = Text.likePattern(q);
        List<Specification<Task>> filters = Stream.<Specification<Task>>of(
                        pattern == null ? null : (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        assigneeId == null ? null : (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId),
                        status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status),
                        due == null ? null : dueFilter(due, today),
                        companyId == null ? null : (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId),
                        dealId == null ? null : (root, query, cb) -> cb.equal(root.get("deal").get("id"), dealId))
                .filter(Objects::nonNull)
                .toList();
        return PageResponse.of(tasks.findAll(Specification.allOf(filters), pageable), t -> TaskResponse.from(t, today));
    }

    // Every due window only makes sense for open tasks: a done task is never "overdue".
    // كل نطاقات الاستحقاق تخص المهام المفتوحة فقط: المهمة المنجزة ما تكون "متأخرة" أبدًا.
    static Specification<Task> dueFilter(Due due, LocalDate today) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), TaskStatus.OPEN),
                switch (due) {
                    case OVERDUE -> cb.lessThan(root.get("dueDate"), today);
                    case TODAY -> cb.equal(root.get("dueDate"), today);
                    // The next 7 days including today. / الأيام السبعة القادمة مع اليوم.
                    case WEEK -> cb.between(root.get("dueDate"), today, today.plusDays(6));
                });
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        return TaskResponse.from(task(id), today());
    }

    @Transactional
    public TaskResponse create(TaskRequest r) {
        AppUser me = currentUser.require();
        // Any team member may hand a task to a colleague (e.g. "call this client back"); inactive users are refused.
        // أي عضو في الفريق يقدر يكلّف زميل بمهمة (مثل "كلّم هالعميل")؛ والمستخدم المعطّل مرفوض.
        AppUser assignee = r.assigneeId() == null ? me : currentUser.activeUser(r.assigneeId());
        Task task = new Task(r.title().trim(), r.dueDate(), assignee, me);
        apply(task, r);
        return TaskResponse.from(tasks.save(task), today());
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest r) {
        Task task = task(id);
        requireEditor(task);
        Versions.check(task.getVersion(), r.version());
        task.setTitle(r.title().trim());
        task.setDueDate(r.dueDate());
        if (r.assigneeId() != null && !r.assigneeId().equals(task.getAssignee().getId())) {
            task.setAssignee(currentUser.activeUser(r.assigneeId()));
        }
        apply(task, r);
        tasks.flush();
        return TaskResponse.from(task, today());
    }

    @Transactional
    public TaskResponse complete(Long id, Long version) {
        Task task = task(id);
        requireEditor(task);
        Versions.check(task.getVersion(), version);
        task.complete(Instant.now());
        tasks.flush();
        return TaskResponse.from(task, today());
    }

    @Transactional
    public TaskResponse reopen(Long id, Long version) {
        Task task = task(id);
        requireEditor(task);
        Versions.check(task.getVersion(), version);
        task.reopen();
        tasks.flush();
        return TaskResponse.from(task, today());
    }

    @Transactional
    public void delete(Long id) {
        Task task = task(id);
        requireEditor(task);
        tasks.delete(task);
    }

    public Task task(Long id) {
        return tasks.findById(id).orElseThrow(() -> ApiException.notFound("task", id));
    }

    /**
     * The assignee, the person who created the task, or an admin may change it.
     * المكلّف أو اللي أنشأ المهمة أو المدير يقدرون يعدّلونها.
     */
    private void requireEditor(Task task) {
        AppUser me = currentUser.require();
        boolean allowed = currentUser.isAdmin(me)
                || me.getId().equals(task.getAssignee().getId())
                || me.getId().equals(task.getCreatedBy().getId());
        if (!allowed) {
            throw new ApiException(ErrorCode.NOT_OWNER);
        }
    }

    private void apply(Task task, TaskRequest r) {
        Links l = links.resolve(r.companyId(), r.dealId(), r.contactId());
        task.setDescription(Text.blankToNull(r.description()));
        task.setPriority(r.priority());
        task.setCompany(l.company());
        task.setDeal(l.deal());
        task.setContact(l.contact());
    }
}
