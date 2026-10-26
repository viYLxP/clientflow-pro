package com.clientflow.activity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.common.PageResponse;
import com.clientflow.activity.ActivityDtos.ActivityRequest;
import com.clientflow.activity.ActivityDtos.ActivityResponse;
import com.clientflow.deal.RecordLinks;
import com.clientflow.deal.RecordLinks.Links;
import com.clientflow.user.AppUser;
import com.clientflow.user.CurrentUserService;

@Service
public class ActivityService {

    // Small allowance for clock differences between the browser and the server.
    // سماحية بسيطة لفرق الساعة بين المتصفح والخادم.
    static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private final ActivityRepository activities;
    private final RecordLinks links;
    private final CurrentUserService currentUser;

    public ActivityService(ActivityRepository activities, RecordLinks links, CurrentUserService currentUser) {
        this.activities = activities;
        this.links = links;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> search(Long companyId, Long dealId, Long contactId, ActivityType type,
                                                 Pageable pageable) {
        List<Specification<Activity>> filters = Stream.<Specification<Activity>>of(
                        companyId == null ? null : (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId),
                        dealId == null ? null : (root, query, cb) -> cb.equal(root.get("deal").get("id"), dealId),
                        contactId == null ? null : (root, query, cb) -> cb.equal(root.get("contact").get("id"), contactId),
                        type == null ? null : (root, query, cb) -> cb.equal(root.get("type"), type))
                .filter(Objects::nonNull)
                .toList();
        return PageResponse.of(activities.findAll(Specification.allOf(filters), pageable), ActivityResponse::from);
    }

    @Transactional
    public ActivityResponse create(ActivityRequest r) {
        Instant now = Instant.now();
        Instant occurredAt = r.occurredAt() == null ? now : r.occurredAt();
        // A log records what already happened; a future time is almost always a typo. Plans belong in tasks.
        // السجل يوثّق اللي صار؛ والوقت المستقبلي غالبًا غلطة كتابة. والخطط مكانها المهام.
        if (occurredAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("occurredAt", "PastOrPresent")));
        }
        Links l = links.resolve(r.companyId(), r.dealId(), r.contactId());
        // Every activity belongs to a company timeline. / كل نشاط لازم يكون في سجل شركة.
        if (l.company() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("companyId", "NotNull")));
        }
        Activity activity = new Activity(r.type(), r.body().trim(), occurredAt, currentUser.require(), l.company(),
                l.deal(), l.contact());
        return ActivityResponse.from(activities.save(activity));
    }

    @Transactional
    public void delete(Long id) {
        Activity activity = activities.findById(id).orElseThrow(() -> ApiException.notFound("activity", id));
        AppUser me = currentUser.require();
        if (!currentUser.isAdmin(me) && !me.getId().equals(activity.getAuthor().getId())) {
            throw new ApiException(ErrorCode.NOT_OWNER);
        }
        activities.delete(activity);
    }
}
