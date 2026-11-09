import type { ButtonHTMLAttributes } from 'react';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'dark' | 'danger';
};

const variants = {
  primary: 'bg-accent text-ink hover:bg-[#f0cc16]',
  secondary: 'border border-line bg-white text-ink hover:bg-canvas',
  dark: 'bg-ink text-white hover:bg-[#343436]',
  danger: 'bg-red-50 text-danger hover:bg-red-100',
};

export function Button({
  variant = 'primary',
  className = '',
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`}
      {...props}
    />
  );
}
