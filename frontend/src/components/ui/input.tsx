import * as React from 'react'
import { cn } from '@/lib/utils'

export function Input({ className, ...props }: React.ComponentProps<'input'>) {
  return (
    <input
      className={cn(
        'h-9 rounded-md border border-[var(--color-line)] bg-[var(--color-raised)] px-3 text-sm',
        'placeholder:text-[var(--color-muted)] focus:outline-none focus:ring-1 focus:ring-[var(--color-accent)]',
        className,
      )}
      {...props}
    />
  )
}
