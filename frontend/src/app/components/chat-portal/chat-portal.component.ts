import {
  Component, OnInit, AfterViewChecked,
  ElementRef, ViewChild, ChangeDetectorRef
} from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { FormSchema, FormField, FieldCondition } from '../../models/form-schema.model';
import { ClaimAnswerPayload, ClaimSubmitResponse, ClaimTypeConfig, PolicyLookupResponse } from '../../models/claim-api.model';
import { ClaimApiService } from '../../services/claim-api.service';

@Component({
  selector: 'app-chat-portal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './chat-portal.component.html',
  styleUrls: ['./chat-portal.component.scss']
})
export class ChatPortalComponent implements OnInit, AfterViewChecked {
  @ViewChild('activeFieldRef') activeFieldRef!: ElementRef;

  schema: FormSchema | null = null;
  form!: FormGroup;
  answeredFields: { field: FormField; displayValue: string }[] = [];
  currentField: FormField | null = null;
  isSubmitted = false;
  selectedFiles: { [fieldId: string]: File[] } = {};
  isLoading = true;
  today = new Date().toISOString().split('T')[0];
  private shouldScroll = false;

  policy: PolicyLookupResponse | null = null;
  isResolvingPolicy = false;
  policyLookupError: string | null = null;

  isSubmitting = false;
  submitError: string | null = null;
  submitResult: ClaimSubmitResponse | null = null;

  /** Maps a dynamically-generated file field id (e.g. "doc_0") back to the document name required by the backend. */
  private documentLabels: { [fieldId: string]: string } = {};

  constructor(
    private claimApi: ClaimApiService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // Screen 1's only fixed question: the policy number. Everything after it
    // (claim type's questions + required documents) is fetched from the
    // backend once the policy is resolved - see resolvePolicy().
    this.schema = {
      id: 'insurance-claim',
      title: 'Insurance Claim Filing',
      fields: [
        {
          id: 'policy_number',
          type: 'text',
          label: "Welcome! I'm your claims assistant. Let's get started — what is your policy number?",
          required: true,
          placeholder: 'e.g. POL-2024-00123'
        }
      ]
    };
    this.form = this.fb.group({
      policy_number: [null, Validators.required]
    });
    this.isLoading = false;
    this.currentField = this.getNextField();
    this.shouldScroll = true;
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToActive();
      this.shouldScroll = false;
    }
  }

  // ── Navigation ──────────────────────────────────────────────

  getNextField(): FormField | null {
    if (!this.schema) return null;
    const answeredIds = new Set(this.answeredFields.map(a => a.field.id));
    return this.schema.fields.find(f => !answeredIds.has(f.id) && this.evaluateShowIf(f)) || null;
  }

  evaluateShowIf(field: FormField): boolean {
    if (!field.showIf || field.showIf.length === 0) return true;
    return field.showIf.every(c => this.checkCondition(c));
  }

  checkCondition(cond: FieldCondition): boolean {
    const val = this.form?.get(cond.field)?.value;
    switch (cond.operator) {
      case 'equals':   return val === cond.value;
      case 'in':       return Array.isArray(cond.value) && cond.value.includes(val);
      case 'notEmpty': return val !== null && val !== '' && val !== undefined;
      default:         return false;
    }
  }

  // ── Answer / Edit ────────────────────────────────────────────

  onAnswer(field: FormField): void {
    if (this.currentField?.id !== field.id) return;
    const value = this.form.get(field.id)?.value;
    if (field.required !== false && (value === null || value === '' || value === undefined)) return;
    const displayValue = this.getDisplayValue(field, value);

    // Insert at the correct schema-order position so an edit re-inserts in the right slot
    const schemaOrder = this.schema!.fields.map(f => f.id);
    const fieldSchemaIdx = schemaOrder.indexOf(field.id);
    const insertAt = this.answeredFields.findIndex(
      a => schemaOrder.indexOf(a.field.id) > fieldSchemaIdx
    );
    if (insertAt === -1) {
      this.answeredFields.push({ field, displayValue });
    } else {
      this.answeredFields.splice(insertAt, 0, { field, displayValue });
    }

    // Drop any answers whose showIf is no longer satisfied (e.g. AUTO fields after switching to HEALTH)
    this.pruneInvalidAnsweredFields();

    if (field.id === 'policy_number') {
      this.resolvePolicy(value);
      return;
    }

    this.currentField = this.getNextField();
    this.shouldScroll = true;
    this.cdr.detectChanges();
  }

  onEditField(field: FormField): void {
    const idx = this.answeredFields.findIndex(a => a.field.id === field.id);
    if (idx === -1) return;
    // Remove only this field; subsequent answers are preserved
    this.answeredFields.splice(idx, 1);
    this.currentField = field;
    this.shouldScroll = true;
    this.cdr.detectChanges();
  }

  private pruneInvalidAnsweredFields(): void {
    const invalid = this.answeredFields.filter(a => !this.evaluateShowIf(a.field));
    if (invalid.length === 0) return;
    invalid.forEach(a => {
      this.form.get(a.field.id)?.setValue(null);
      delete this.selectedFiles[a.field.id];
    });
    const invalidIds = new Set(invalid.map(a => a.field.id));
    this.answeredFields = this.answeredFields.filter(a => !invalidIds.has(a.field.id));
  }

  onTextareaEnter(event: Event, field: FormField): void {
    const ke = event as KeyboardEvent;
    if (!ke.shiftKey) {
      event.preventDefault();
      this.onAnswer(field);
    }
  }

  onRadioSelect(field: FormField, value: string): void {
    this.form.get(field.id)?.setValue(value);
    setTimeout(() => this.onAnswer(field), 300);
  }

  onFileSelected(event: Event, field: FormField): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectedFiles[field.id] = Array.from(input.files);
      this.form.get(field.id)?.setValue(this.selectedFiles[field.id].map(f => f.name).join(', '));
    }
  }

  onSkipFile(field: FormField): void {
    this.form.get(field.id)?.setValue('skipped');
    this.answeredFields.push({ field, displayValue: 'Skipped' });
    this.currentField = this.getNextField();
    this.shouldScroll = true;
    this.cdr.detectChanges();
  }

  // ── Policy resolution (Screen 1 → backend) ───────────────────

  private resolvePolicy(policyNumber: string): void {
    this.policyLookupError = null;
    this.isResolvingPolicy = true;
    this.currentField = null;
    this.cdr.detectChanges();

    this.claimApi.lookupPolicy(policyNumber).subscribe({
      next: policy => {
        this.policy = policy;
        this.claimApi.getConfig(policy.claimType).subscribe({
          next: config => {
            this.appendDynamicFields(config);
            this.isResolvingPolicy = false;
            this.currentField = this.getNextField();
            this.shouldScroll = true;
            this.cdr.detectChanges();
          },
          error: () => this.failPolicyResolution('Could not load the claim form for this policy. Please try again.')
        });
      },
      error: () => this.failPolicyResolution('We could not find a policy with that number. Please check and try again.')
    });
  }

  private failPolicyResolution(message: string): void {
    this.isResolvingPolicy = false;
    this.policyLookupError = message;
    this.answeredFields = this.answeredFields.filter(a => a.field.id !== 'policy_number');
    this.currentField = this.schema!.fields.find(f => f.id === 'policy_number') || null;
    this.shouldScroll = true;
    this.cdr.detectChanges();
  }

  private appendDynamicFields(config: ClaimTypeConfig): void {
    const questionFields: FormField[] = config.questions.map(q => ({
      id: q.questionId,
      type: this.mapFieldType(q.fieldType),
      label: q.questionText,
      required: true
    }));

    const documentFields: FormField[] = config.requiredDocuments.map((docName, idx) => {
      const id = `doc_${idx}`;
      this.documentLabels[id] = docName;
      this.selectedFiles[id] = [];
      return {
        id,
        type: 'file',
        label: `Please upload: ${docName}`,
        required: true,
        accept: '.jpg,.jpeg,.png,.pdf,.doc,.docx',
        multiple: false
      };
    });

    const commentsField: FormField = {
      id: 'additional_comments',
      type: 'textarea',
      label: "Any additional comments or information you'd like to add? (Optional)",
      required: false,
      placeholder: 'Add any extra details, special circumstances, or notes for the claims team...'
    };

    const newFields = [...questionFields, ...documentFields, commentsField];
    this.schema!.fields.push(...newFields);
    newFields.forEach(f => {
      this.form.addControl(f.id, this.fb.control(null, f.required !== false ? Validators.required : []));
    });
  }

  // Backend dropdown questions don't carry an options list yet, so they're rendered as free text for now.
  private mapFieldType(backendFieldType: string): FormField['type'] {
    return backendFieldType === 'date' ? 'date' : 'text';
  }

  // ── Submit ────────────────────────────────────────────────────

  onSubmit(): void {
    if (!this.policy || !this.schema) return;

    const answers: ClaimAnswerPayload[] = this.schema.fields
      .filter(f => f.type !== 'file' && f.id !== 'policy_number' && f.id !== 'additional_comments')
      .map(f => ({
        questionId: f.id,
        questionText: f.label,
        answerText: String(this.form.get(f.id)?.value ?? '')
      }));

    const files: File[] = Object.values(this.selectedFiles).flat();

    const claim = {
      customerId: this.policy.customerId,
      policyId: this.policy.policyId,
      claimType: this.policy.claimType,
      claimReason: '',
      freeText: this.form.get('additional_comments')?.value ?? '',
      answers
    };

    this.isSubmitting = true;
    this.submitError = null;

    this.claimApi.submit(claim, files).subscribe({
      next: response => {
        this.isSubmitting = false;
        if (response.fileErrors && response.fileErrors.length) {
          this.submitError = response.fileErrors.join('; ');
          this.cdr.detectChanges();
          return;
        }
        this.submitResult = response;
        this.isSubmitted = true;
        this.currentField = null;
        this.shouldScroll = true;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isSubmitting = false;
        this.submitError = 'Something went wrong submitting your claim. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }

  // ── Display helpers ──────────────────────────────────────────

  getDisplayValue(field: FormField, value: any): string {
    if (value === null || value === undefined || value === '') return '—';
    if (field.type === 'file') {
      const files = this.selectedFiles[field.id];
      return files?.length ? `${files.length} file(s)` : '—';
    }
    if (field.options) {
      return field.options.find(o => o.value === value)?.label ?? String(value);
    }
    return String(value);
  }

  getSelectedRadioValue(fieldId: string): string | null {
    return this.form?.get(fieldId)?.value ?? null;
  }

  isReadyToSubmit(): boolean {
    return !!this.schema && this.currentField === null && !this.isSubmitted && !this.isResolvingPolicy;
  }

  // Merges answered fields and the active field in schema order so they render
  // interleaved. The unique key ('a:' vs 'c:' prefix) forces Angular to destroy
  // and recreate the DOM when a field transitions between answered and active.
  get displayItems(): { key: string; isActive: boolean; field: FormField; displayValue: string }[] {
    if (!this.schema || !this.form) return [];
    const items: { key: string; isActive: boolean; field: FormField; displayValue: string }[] = [];
    for (const schemaField of this.schema.fields) {
      const answered = this.answeredFields.find(a => a.field.id === schemaField.id);
      if (answered) {
        items.push({ key: 'a:' + schemaField.id, isActive: false, field: answered.field, displayValue: answered.displayValue });
      } else if (this.currentField?.id === schemaField.id) {
        items.push({ key: 'c:' + schemaField.id, isActive: true, field: schemaField, displayValue: '' });
      }
    }
    return items;
  }

  totalApplicableFields(): number {
    if (!this.schema) return 0;
    return this.schema.fields.filter(
      f => this.evaluateShowIf(f) || this.answeredFields.some(a => a.field.id === f.id)
    ).length;
  }

  progressPercent(): number {
    const total = this.totalApplicableFields();
    return total > 0 ? Math.round((this.answeredFields.length / total) * 100) : 0;
  }

  private scrollToActive(): void {
    try {
      if (this.activeFieldRef) {
        this.activeFieldRef.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    } catch {}
  }
}
