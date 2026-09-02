import { cva, type VariantProps } from 'class-variance-authority'
import * as React from 'react'
import { cn } from '@/lib/utils'

const button = cva(
  'inline-flex items-center justify-center gap-2 rounded-md text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed',
  {
    variants: {
      variant: {
        solid: 'bg-[var(--color-accent)] text-[#0f1115] hover:bg-[var(--color-accent)]/85',
        outline:
          'border border-[var(--color-line)] text-[var(--color-body)] hover:bg-[var(--color-raised)]',
        ghost: 'text-[var(--color-muted)] hover:text-[var(--color-body)]',
      },
      size: { sm: 'h-8 px-3', md: 'h-9 px-4' },
    },
    defaultVariants: { variant: 'outline', size: 'sm' },
  },
)

export function Button({
  className,
  variant,
  size,
  ...props
}: React.ComponentProps<'button'> & VariantProps<typeof button>) {
  return <button className={cn(button({ variant, size }), className)} {...props} />
}
