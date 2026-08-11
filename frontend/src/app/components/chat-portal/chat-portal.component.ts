import {
  Component, OnInit, AfterViewChecked,
  ElementRef, ViewChild, ChangeDetectorRef
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { FormSchema, FormField, FieldCondition } from '../../models/form-schema.model';

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
  claimRef = '';
  selectedFiles: { [fieldId: string]: File[] } = {};
  isLoading = true;
  today = new Date().toISOString().split('T')[0];
  private shouldScroll = false;

  constructor(
    private http: HttpClient,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.http.get<FormSchema>('/assets/form-schema.json').subscribe({
      next: schema => {
        this.schema = schema;
        const controls: Record<string, any> = {};
        schema.fields.forEach(f => {
          controls[f.id] = [null, f.required !== false ? Validators.required : []];
        });
        this.form = this.fb.group(controls);
        this.isLoading = false;
        this.currentField = this.getNextField();
        this.shouldScroll = true;
        this.cdr.detectChanges();
      },
      error: err => {
        console.error('Failed to load form schema', err);
        this.isLoading = false;
      }
    });
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

  onSubmit(): void {
    this.claimRef = Math.random().toString(36).slice(2, 8).toUpperCase();
    this.isSubmitted = true;
    this.currentField = null;
    this.shouldScroll = true;
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
    return !!this.schema && this.currentField === null && !this.isSubmitted;
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
