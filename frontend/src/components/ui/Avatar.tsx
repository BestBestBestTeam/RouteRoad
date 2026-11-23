type AvatarProps = {
  initials: string;
  tone?: 'yellow' | 'purple' | 'mint' | 'red' | 'blue' | 'gray';
  className?: string;
};

const tones = {
  yellow: 'bg-[#fff3b1]',
  purple: 'bg-[#ece6ff]',
  mint: 'bg-[#e2f2ed]',
  red: 'bg-[#fcebe7]',
  blue: 'bg-[#e7efff]',
  gray: 'bg-[#e8eaee]',
};

export function Avatar({ initials, tone = 'yellow', className = '' }: AvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={`inline-flex ${className.includes('size-') ? '' : 'size-9'} shrink-0 items-center justify-center rounded-full text-xs font-semibold ${tones[tone]} ${className}`}
    >
      {initials}
    </span>
  );
}
