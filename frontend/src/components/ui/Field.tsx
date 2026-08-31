import React from 'react';
import { Icon } from './Icon';

/*
 * Form controls.
 *
 * Every control is wrapped by Field, which owns the label/hint/error relationship and the ARIA
 * wiring. Screens therefore cannot accidentally ship an input whose error message is visible but
 * not announced.
 */

interface FieldShellProps {
  id: string;
  label?: string;
  hint?: string;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
  className?: string;
}

export function Field({ id, label, hint, error, required, children, className }: FieldShellProps) {
  return (
    <div className={['field', className ?? ''].filter(Boolean).join(' ')}>
      {label && (
        /*
         * The asterisk sits outside the <label> deliberately. Inside it, it becomes part of the
         * accessible name — the field announces as "Password star" — and no longer matches the
         * label a person would look for. Requiredness reaches assistive technology through the
         * control's own `required` attribute instead.
         */
        <span className="field__label-row">
          <label className="field__label" htmlFor={id}>
            {label}
          </label>
          {required && (
            <span className="field__required" aria-hidden="true">
              *
            </span>
          )}
        </span>
      )}
      {children}
      {hint && !error && (
        <span className="field__hint" id={`${id}-hint`}>
          {hint}
        </span>
      )}
      {error && (
        <span className="field__error" id={`${id}-error`}>
          <Icon name="alert" size={14} />
          {error}
        </span>
      )}
    </div>
  );
}

function describedBy(id: string, hint?: string, error?: string): string | undefined {
  if (error) return `${id}-error`;
  if (hint) return `${id}-hint`;
  return undefined;
}

export interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'id'> {
  id: string;
  label?: string;
  hint?: string;
  error?: string;
  fieldClassName?: string;
  inputSize?: 'md' | 'lg';
}

/** Forwards its ref so a screen can put the cursor where the work is — the till's amount field. */
export const Input = React.forwardRef<HTMLInputElement, InputProps>(function Input(
  { id, label, hint, error, fieldClassName, inputSize = 'md', className, ...props },
  ref
) {
  return (
    <Field id={id} label={label} hint={hint} error={error} required={props.required} className={fieldClassName}>
      <input
        {...props}
        ref={ref}
        id={id}
        className={['control', inputSize === 'lg' ? 'control--lg' : '', error ? 'control--invalid' : '', className ?? '']
          .filter(Boolean)
          .join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
      />
    </Field>
  );
});

export interface TextareaProps extends Omit<React.TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> {
  id: string;
  label?: string;
  hint?: string;
  error?: string;
  fieldClassName?: string;
}

export function Textarea({ id, label, hint, error, fieldClassName, className, ...props }: TextareaProps) {
  return (
    <Field id={id} label={label} hint={hint} error={error} required={props.required} className={fieldClassName}>
      <textarea
        {...props}
        id={id}
        className={['control', 'control--textarea', error ? 'control--invalid' : '', className ?? '']
          .filter(Boolean)
          .join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
      />
    </Field>
  );
}

export interface SelectOption {
  label: string;
  value: string;
  disabled?: boolean;
}

export interface SelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'id'> {
  id: string;
  label?: string;
  hint?: string;
  error?: string;
  options: SelectOption[];
  /** Text for the empty option. Pass null for a select that must always hold a value. */
  placeholder?: string | null;
  fieldClassName?: string;
}

export function Select({
  id,
  label,
  hint,
  error,
  options,
  placeholder = 'All',
  fieldClassName,
  className,
  ...props
}: SelectProps) {
  return (
    <Field id={id} label={label} hint={hint} error={error} required={props.required} className={fieldClassName}>
      <select
        {...props}
        id={id}
        className={['control', error ? 'control--invalid' : '', className ?? ''].filter(Boolean).join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
      >
        {placeholder !== null && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </option>
        ))}
      </select>
    </Field>
  );
}

export interface CheckboxProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'id' | 'type'> {
  id: string;
  label: string;
  hint?: string;
}

export function Checkbox({ id, label, hint, className, ...props }: CheckboxProps) {
  return (
    <label className={['checkbox', className ?? ''].filter(Boolean).join(' ')} htmlFor={id}>
      <input {...props} id={id} type="checkbox" aria-describedby={hint ? `${id}-hint` : undefined} />
      <span className="checkbox__text">
        {label}
        {hint && (
          <span className="checkbox__hint" id={`${id}-hint`}>
            {hint}
          </span>
        )}
      </span>
    </label>
  );
}

export interface SearchInputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'id'> {
  id: string;
  label?: string;
  hint?: string;
  fieldClassName?: string;
  inputSize?: 'md' | 'lg';
}

export const SearchInput = React.forwardRef<HTMLInputElement, SearchInputProps>(function SearchInput(
  { id, label, hint, fieldClassName, inputSize = 'md', className, ...props },
  ref
) {
  return (
    <Field id={id} label={label} hint={hint} className={fieldClassName}>
      <span className="search-input">
        <span className="search-input__icon">
          <Icon name="search" size={16} />
        </span>
        <input
          {...props}
          ref={ref}
          id={id}
          type="search"
          className={['control', inputSize === 'lg' ? 'control--lg' : '', className ?? ''].filter(Boolean).join(' ')}
          aria-describedby={hint ? `${id}-hint` : undefined}
        />
      </span>
    </Field>
  );
});
