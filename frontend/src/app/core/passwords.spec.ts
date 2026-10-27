import { FormControl, FormGroup } from '@angular/forms';

import { matches } from './passwords';

describe('matches', () => {
  it('flags a confirmation that differs from the password', () => {
    const form = new FormGroup({
      password: new FormControl('Secret@123'),
      confirm: new FormControl('', matches('password')),
    });
    const confirm = form.controls.confirm;
    // Empty is left to "required". / الفاضي يتكفل فيه "required".
    expect(confirm.errors).toBeNull();
    confirm.setValue('Secret@124');
    expect(confirm.errors).toEqual({ mismatch: true });
    confirm.setValue('Secret@123');
    expect(confirm.errors).toBeNull();
  });
});
