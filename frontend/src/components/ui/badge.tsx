import { cva, type VariantProps } from 'class-variance-authority'
import * as React from 'react'
import { cn } from '@/lib/utils'

const badge = cva(
  'inline-flex items-center rounded-full border px-2.5 py-0.5 text-[11px] font-medium tracking-wide uppercase',
  {
    variants: {
      variant: {
        abandoned:
          'border-[var(--color-abandoned)]/40 text-[var(--color-abandoned)] bg-[var(--color-abandoned)]/10',
        decayed:
          'border-[var(--color-decayed)]/40 text-[var(--color-decayed)] bg-[var(--color-decayed)]/10',
        neutral: 'border-[var(--color-line)] text-[var(--color-muted)]',
      },
    },
    defaultVariants: { variant: 'neutral' },
  },
)

export function Badge({
  className,
  variant,
  ...props
}: React.ComponentProps<'span'> & VariantProps<typeof badge>) {
  return <span className={cn(badge({ variant }), className)} {...props} />
}
