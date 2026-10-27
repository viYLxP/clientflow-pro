import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * "Confirm password" must equal the sibling control named {@code other}. The form re-checks it on submit,
 * because changing the first password does not re-run this control's validators by itself.
 * "تأكيد كلمة المرور" لازم يساوي الحقل الشقيق اللي اسمه {@code other}. النموذج يعيد فحصه عند الإرسال،
 * لأن تغيير كلمة المرور الأولى ما يعيد تشغيل تحقق هذا الحقل لحاله.
 */
export function matches(other: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.parent?.get(other)?.value;
    return control.value && value !== control.value ? { mismatch: true } : null;
  };
}
