export interface FieldCondition {
  field: string;
  operator: 'equals' | 'in' | 'notEmpty';
  value?: string | string[];
}

export interface FieldOption {
  value: string;
  label: string;
}

export interface FormField {
  id: string;
  type: 'text' | 'textarea' | 'radio' | 'dropdown' | 'date' | 'file' | 'submit';
  label: string;
  required?: boolean;
  placeholder?: string;
  options?: FieldOption[];
  accept?: string;
  multiple?: boolean;
  inputType?: string;
  showIf?: FieldCondition[];
}

export interface FormSchema {
  id: string;
  title: string;
  fields: FormField[];
}
