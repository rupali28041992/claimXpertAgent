import {
  Component, OnInit, AfterViewChecked,
  ElementRef, ViewChild, ChangeDetectorRef
} from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { FormField } from '../../models/form-schema.model';
import { ClaimsService, DocumentCategory, QuestionnaireState } from '../../services/claims.service';
import { ClaimSubmitResponse, DocumentResult, PolicyLookupResponse } from '../../models/claim-api.model';

const OTHERS_DOC: DocumentCategory = {
  type: 'Others',
  description: 'Any other supporting documents not listed above'
};


@Component({
  selector: 'app-chat-portal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './chat-portal.component.html',
  styleUrls: ['./chat-portal.component.scss']
})
export class ChatPortalComponent implements OnInit, AfterViewChecked {
  @ViewChild('activeFieldRef') activeFieldRef!: ElementRef;

  form!: FormGroup;
  dynamicQuestions: FormField[] = [];
  answeredFields: { field: FormField; displayValue: string }[] = [];
  currentField: FormField | null = null;

  /** Answers accumulated so far, sent to GoRules on each step. */
  currentAnswers: Record<string, string> = {};

  /** Set to true when GoRules returns isComplete — triggers file-upload addition. */
  questionsComplete = false;

  /** Derived by GoRules from user answers; used at submit time. */
  derivedClaimType: string | null = null;
  derivedClaimReason: string | null = null;

  // Document upload state — categories driven by requiredDocuments from GoRules
  requiredDocuments: DocumentCategory[] = [];
  documentUploads: Record<string, File[]> = {};
  docsConfirmed = false;

  get allDocCategories(): DocumentCategory[] {
    return [...this.requiredDocuments, OTHERS_DOC];
  }

  isSubmitted = false;
  isSubmitting = false;
  isLoading = true;
  submitError = '';
  claimRef = '';

  /** Field descriptor for the submit-zone document uploader. */
  readonly docsField: FormField = {
    id: 'supporting_documents',
    type: 'file',
    label: 'Supporting Documents',
    required: false,
    accept: '.jpg,.jpeg,.png,.pdf,.doc,.docx',
    multiple: true
  };

  selectedFiles: Record<string, File[] | undefined> = {};
  dragOverField: string | null = null;
  policyNumber = '';
  policyCheckState: 'idle' | 'checking' | 'found' | 'not-found' = 'idle';
  verifiedPolicyId = '';
  verifiedHolderName = '';

  today = new Date().toISOString().split('T')[0];
  private shouldScroll = false;

  policy: PolicyLookupResponse | null = null;
  isResolvingPolicy = false;
  policyLookupError: string | null = null;
  submitResult: ClaimSubmitResponse | null = null;

  /** Maps a dynamically-generated file field id (e.g. "doc_0") back to the document name required by the backend. */
  private documentLabels: { [fieldId: string]: string } = {};

  constructor(
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef,
    private claimsService: ClaimsService
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({});
    this.loadInitialQuestions();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToActive();
      this.shouldScroll = false;
    }
  }

  // ── GoRules integration ──────────────────────────────────────

  private loadInitialQuestions(): void {
    this.claimsService.getNextQuestions({}).subscribe({
      next: state => {
        this.applyState(state);
        this.isLoading = false;
        this.shouldScroll = true;
        this.cdr.detectChanges();
      },
      error: err => {
        console.error('Failed to load initial questions from GoRules', err);
        this.isLoading = false;
      }
    });
  }

  private fetchNextQuestions(): void {
    this.claimsService.getNextQuestions(this.currentAnswers).subscribe({
      next: state => {
        this.applyState(state);
        this.shouldScroll = true;
        this.cdr.detectChanges();
      },
      error: err => console.error('GoRules question engine error', err)
    });
  }

  private applyState(state: QuestionnaireState): void {
    const stateIds = new Set(state.questions.map(q => q.id));
    const answeredIds = new Set(this.answeredFields.map(a => a.field.id));

    // Prune answered fields that no longer belong to the current question set
    // (handles the case where the user edits incident_category and switches type).
    const pruned = this.answeredFields.filter(a => stateIds.has(a.field.id));
    if (pruned.length !== this.answeredFields.length) {
      this.answeredFields
        .filter(a => !stateIds.has(a.field.id))
        .forEach(a => {
          this.form.get(a.field.id)?.setValue(null);
          delete this.currentAnswers[a.field.id];
          delete this.selectedFiles[a.field.id];
        });
      this.answeredFields = pruned;
    }

    // Sync question list: add new, keep answered ones that were removed from state
    const newQuestions: FormField[] = state.questions.map(q => q as FormField);
    for (const answered of this.answeredFields) {
      if (!newQuestions.find(q => q.id === answered.field.id)) {
        newQuestions.push(answered.field);
      }
    }

    // Ensure a form control exists for every new question
    for (const q of newQuestions) {
      if (!this.form.contains(q.id)) {
        this.form.addControl(
          q.id,
          this.fb.control(null, q.required !== false ? Validators.required : [])
        );
      }
    }

    this.dynamicQuestions = newQuestions;

    if (state.isComplete && !this.questionsComplete) {
      this.questionsComplete = true;
      this.setRequiredDocuments(state.requiredDocuments || []);
    } else if (!state.isComplete) {
      if (this.questionsComplete) {
        this.questionsComplete = false;
        this.docsConfirmed = false;
      }
    }

    if (state.claimType) this.derivedClaimType = state.claimType;
    if (state.claimReason) this.derivedClaimReason = state.claimReason;

    this.currentField = this.getNextField();
  }

  private setRequiredDocuments(docs: DocumentCategory[]): void {
    const newTypes = docs.map(d => d.type).join(',');
    const oldTypes = this.requiredDocuments.map(d => d.type).join(',');
    if (newTypes !== oldTypes) {
      this.documentUploads = {};
      this.docsConfirmed = false;
    }
    this.requiredDocuments = docs;
  }

  // ── Navigation ──────────────────────────────────────────────

  getNextField(): FormField | null {
    const answeredIds = new Set(this.answeredFields.map(a => a.field.id));
    return this.dynamicQuestions.find(q => !answeredIds.has(q.id)) || null;
  }

  // ── Answer / Edit ────────────────────────────────────────────

  onAnswer(field: FormField): void {
    if (this.currentField?.id !== field.id) return;
    const value = this.form.get(field.id)?.value;
    if (field.required !== false && (value === null || value === '' || value === undefined)) return;

    const displayValue = this.getDisplayValue(field, value);

    // Insert into answeredFields preserving dynamicQuestions order
    const schemaOrder = this.dynamicQuestions.map(q => q.id);
    const fieldIdx = schemaOrder.indexOf(field.id);
    const insertAt = this.answeredFields.findIndex(
      a => schemaOrder.indexOf(a.field.id) > fieldIdx
    );
    if (insertAt === -1) {
      this.answeredFields.push({ field, displayValue });
    } else {
      this.answeredFields.splice(insertAt, 0, { field, displayValue });
    }

    // File field is frontend-only; no need to call GoRules for it
    if (field.type === 'file') {
      this.currentField = this.getNextField();
      this.shouldScroll = true;
      this.cdr.detectChanges();
      return;
    }

    // Record answer and let GoRules decide what comes next
    this.currentAnswers[field.id] = value;
    this.fetchNextQuestions();
  }

  onEditField(field: FormField): void {
    const idx = this.answeredFields.findIndex(a => a.field.id === field.id);
    if (idx === -1) return;
    this.answeredFields.splice(idx, 1);
    delete this.currentAnswers[field.id];
    this.form.get(field.id)?.setValue(null);
    // Editing a business question may change required documents
    this.docsConfirmed = false;
    this.questionsComplete = false;
    this.currentField = field;
    this.shouldScroll = true;
    this.cdr.detectChanges();
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

  openDatePicker(id: string): void {
    const el = document.getElementById(id) as HTMLInputElement;
    try { el?.showPicker(); } catch (_) {}
  }

  toDisplayDate(value: string | null | undefined): string {
    if (!value || value.length !== 10) return 'DD/MM/YYYY';
    const p = value.split('-');
    return p.length === 3 ? `${p[2]}/${p[1]}/${p[0]}` : 'DD/MM/YYYY';
  }

  onFileSelected(event: Event, field: FormField, append = false): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      const incoming = Array.from(input.files);
      this.selectedFiles[field.id] = append && this.selectedFiles[field.id]?.length
        ? [...this.selectedFiles[field.id]!, ...incoming]
        : incoming;
      this.form.get(field.id)?.setValue(this.selectedFiles[field.id]!.map(f => f.name).join(', '));
      input.value = '';
    }
  }

  onDragOver(event: DragEvent, fieldId: string): void {
    event.preventDefault();
    this.dragOverField = fieldId;
  }

  onDragLeave(): void {
    this.dragOverField = null;
  }

  onDrop(event: DragEvent, field: FormField): void {
    event.preventDefault();
    this.dragOverField = null;
    const incoming = Array.from(event.dataTransfer?.files || []);
    if (incoming.length) {
      this.selectedFiles[field.id] = this.selectedFiles[field.id]?.length
        ? [...this.selectedFiles[field.id]!, ...incoming]
        : incoming;
      this.form.get(field.id)?.setValue(this.selectedFiles[field.id]!.map(f => f.name).join(', '));
      this.cdr.detectChanges();
    }
  }

  removeFile(fieldId: string, index: number): void {
    const files = this.selectedFiles[fieldId];
    if (!files) return;
    files.splice(index, 1);
    if (files.length === 0) {
      delete this.selectedFiles[fieldId];
      this.form.get(fieldId)?.setValue(null);
    } else {
      this.form.get(fieldId)?.setValue(files.map(f => f.name).join(', '));
    }
    this.cdr.detectChanges();
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  getFileIcon(filename: string): string {
    const ext = filename.split('.').pop()?.toLowerCase() ?? '';
    const map: Record<string, string> = {
      pdf: '📄', jpg: '🖼️', jpeg: '🖼️', png: '🖼️',
      doc: '📝', docx: '📝', xls: '📊', xlsx: '📊', txt: '📃'
    };
    return map[ext] ?? '📎';
  }

  // ── Document category upload ─────────────────────────────────

  getFilesForType(docType: string): File[] {
    return this.documentUploads[docType] || [];
  }

  totalDocCount(): number {
    return Object.values(this.documentUploads)
      .reduce((sum, files) => sum + (files?.length || 0), 0);
  }

  onDocFileSelected(event: Event, docType: string): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const incoming = Array.from(input.files);
    const existing = this.documentUploads[docType] || [];
    this.documentUploads = { ...this.documentUploads, [docType]: [...existing, ...incoming] };
    input.value = '';
    this.cdr.detectChanges();
  }

  removeDocFile(docType: string, index: number): void {
    const files = [...(this.documentUploads[docType] || [])];
    files.splice(index, 1);
    this.documentUploads = { ...this.documentUploads, [docType]: files };
    this.cdr.detectChanges();
  }

  onConfirmDocuments(): void {
    this.docsConfirmed = true;
    this.shouldScroll = true;
    this.cdr.detectChanges();
  }

  onSkipDocuments(): void {
    this.docsConfirmed = true;
    this.shouldScroll = true;
    this.cdr.detectChanges();
  }

  onEditDocuments(): void {
    this.docsConfirmed = false;
    this.shouldScroll = true;
    this.cdr.detectChanges();
  }

  onSkipFile(field: FormField): void {
    this.form.get(field.id)?.setValue('skipped');
    this.answeredFields.push({ field, displayValue: 'Skipped' });
    this.currentField = this.getNextField();
    this.shouldScroll = true;
    this.cdr.detectChanges();
  }

  // ── Policy gate ─────────────────────────────────────────────

  checkPolicy(): void {
    if (!this.policyNumber.trim()) return;
    this.policyCheckState = 'checking';
    this.claimsService.verifyPolicy(this.policyNumber.trim()).subscribe({
      next: res => {
        if (res.valid) {
          this.verifiedPolicyId = res.policyId;
          this.verifiedHolderName = res.holderName || '';
          this.policyCheckState = 'found';
        } else {
          this.policyCheckState = 'not-found';
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.policyCheckState = 'not-found';
        this.cdr.detectChanges();
      }
    });
  }

  retryPolicy(): void {
    this.policyNumber = '';
    this.policyCheckState = 'idle';
  }

  // ── Submit ───────────────────────────────────────────────────

  onSubmit(): void {
    this.isSubmitting = true;
    this.submitError = '';

    // Build filename → document category map from the upload UI
    const fileDocumentTypes: Record<string, string> = {};
    Object.entries(this.documentUploads).forEach(([docType, files]) => {
      (files || []).forEach(f => { fileDocumentTypes[f.name] = docType; });
    });

    const claimRequest = {
      customerId: 'guest',
      policyId: this.verifiedPolicyId || 'POL-UNKNOWN',
      claimType: this.derivedClaimType || 'UNKNOWN',
      claimReason: this.derivedClaimReason || 'Unknown',
      freeText: this.currentAnswers['diagnosis']
        || this.currentAnswers['incident_type']
        || this.currentAnswers['issue_type']
        || this.currentAnswers['cause_of_death']
        || '',
      answers: this.answeredFields.map(a => ({
        questionId: a.field.id,
        questionText: a.field.label,
        answerText: this.form.get(a.field.id)?.value ?? ''
      })),
      fileDocumentTypes
    };

    const fd = new FormData();
    fd.append('claim', new Blob([JSON.stringify(claimRequest)], { type: 'application/json' }));

    const allFiles = Object.values(this.documentUploads).flat().filter(Boolean) as File[];
    if (allFiles.length === 0) {
      fd.append('files', new Blob(['placeholder'], { type: 'application/pdf' }), 'placeholder.pdf');
    } else {
      allFiles.forEach(f => fd.append('files', f, f.name));
    }

    this.claimsService.submit(fd).subscribe({
      next: response => {
        const failedDocs = response.documents.filter((d: DocumentResult) => !d.valid);
        if (response.status === 'FAILED' && failedDocs.length) {
          this.submitError = failedDocs.flatMap((d: DocumentResult) => d.errors).join(' | ');
          this.isSubmitting = false;
          return;
        }
        this.claimRef = response.claimId;
        this.submitResult = response;
        this.isSubmitted = true;
        this.isSubmitting = false;
        this.currentField = null;
        this.shouldScroll = true;
      },
      error: () => {
        this.submitError = 'Failed to submit claim. Please check your connection and try again.';
        this.isSubmitting = false;
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
    return this.questionsComplete && this.docsConfirmed && !this.isSubmitted;
  }

  get displayItems(): { key: string; isActive: boolean; field: FormField; displayValue: string }[] {
    if (!this.form) return [];
    const items: { key: string; isActive: boolean; field: FormField; displayValue: string }[] = [];
    for (const q of this.dynamicQuestions) {
      const answered = this.answeredFields.find(a => a.field.id === q.id);
      if (answered) {
        items.push({ key: 'a:' + q.id, isActive: false, field: answered.field, displayValue: answered.displayValue });
      } else if (this.currentField?.id === q.id) {
        items.push({ key: 'c:' + q.id, isActive: true, field: q, displayValue: '' });
      }
    }
    return items;
  }

  totalApplicableFields(): number {
    return this.dynamicQuestions.length + (this.questionsComplete ? 1 : 0); // +1 for doc upload step
  }

  progressPercent(): number {
    const total = this.totalApplicableFields();
    const done = this.answeredFields.length + (this.docsConfirmed ? 1 : 0);
    return total > 0 ? Math.round((done / total) * 100) : 0;
  }

  private scrollToActive(): void {
    try {
      if (this.activeFieldRef) {
        this.activeFieldRef.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    } catch {}
  }
}
