import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { PolicyApiService } from '../../services/policy-api.service';

@Component({
  selector: 'app-add-policy',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './add-policy.component.html',
  styleUrls: ['./add-policy.component.scss']
})
export class AddPolicyComponent {
  form: FormGroup;
  claimTypes = ['LIFE', 'MEDICAL', 'MOTOR', 'TRAVEL'];

  isSaving = false;
  saveError: string | null = null;
  savedPolicyNumber: string | null = null;

  constructor(private fb: FormBuilder, private policyApi: PolicyApiService) {
    this.form = this.fb.group({
      policyNumber: [null, Validators.required],
      customerId: [null, Validators.required],
      claimType: [null, Validators.required],
      policyholderName: [null, Validators.required],
      sumInsured: [null, [Validators.required, Validators.min(1)]],
      startDate: [null, Validators.required],
      endDate: [null, Validators.required]
    });
  }

  onSave(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    this.saveError = null;

    const value = this.form.value;
    this.policyApi.create({
      policyNumber: value.policyNumber,
      customerId: value.customerId,
      claimType: value.claimType,
      policyholderName: value.policyholderName,
      sumInsured: value.sumInsured,
      startDate: new Date(value.startDate).toISOString(),
      endDate: new Date(value.endDate).toISOString()
    }).subscribe({
      next: policy => {
        this.isSaving = false;
        this.savedPolicyNumber = policy.policyNumber;
        this.form.reset();
      },
      error: () => {
        this.isSaving = false;
        this.saveError = 'Could not save this policy. Please check the details and try again.';
      }
    });
  }

  onAddAnother(): void {
    this.savedPolicyNumber = null;
  }
}
