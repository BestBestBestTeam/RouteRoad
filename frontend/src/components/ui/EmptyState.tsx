import { Icon } from '@/components/ui/Icon';

export function EmptyState({
  title,
  description,
  icon = 'list',
}: {
  title: string;
  description: string;
  icon?: 'list' | 'users' | 'alert' | 'clock';
}) {
  return (
    <section className="rounded-2xl border border-line bg-white px-6 py-12 text-center">
      <span className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-accent-soft">
        <Icon name={icon} className="size-6" />
      </span>
      <h2 className="mt-4 text-lg font-semibold">{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-sm leading-relaxed text-muted">{description}</p>
    </section>
  );
}
