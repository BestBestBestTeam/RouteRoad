import {
  ArrowLeft,
  ArrowRight,
  Bell,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronRight,
  Circle,
  Clock,
  Download,
  LayoutGrid,
  List,
  LocateFixed,
  LogOut,
  MapPin,
  Minus,
  PanelLeftClose,
  PanelLeftOpen,
  Phone,
  Plus,
  RotateCw,
  Route,
  Search,
  TriangleAlert,
  UserRound,
  UsersRound,
  Wifi,
  WifiOff,
  X,
  type LucideIcon,
} from 'lucide-react';

type IconProps = { name: string; className?: string };

const icons: Record<string, LucideIcon> = {
  route: Route,
  grid: LayoutGrid,
  pin: MapPin,
  users: UsersRound,
  alert: TriangleAlert,
  clock: Clock,
  down: ChevronDown,
  calendar: CalendarDays,
  bell: Bell,
  plus: Plus,
  minus: Minus,
  target: LocateFixed,
  chevron: ChevronRight,
  search: Search,
  arrow: ArrowRight,
  wifi: Wifi,
  check: Check,
  list: List,
  user: UserRound,
  back: ArrowLeft,
  phone: Phone,
  download: Download,
  'sidebar-collapse': PanelLeftClose,
  'sidebar-expand': PanelLeftOpen,
  close: X,
  logout: LogOut,
  refresh: RotateCw,
  'wifi-off': WifiOff,
};

export function Icon({ name, className = '' }: IconProps) {
  const Glyph = icons[name] ?? Circle;

  return (
    <span
      aria-hidden="true"
      className={`inline-flex ${className.includes('size-') ? '' : 'size-5'} shrink-0 items-center justify-center ${className}`}
    >
      <Glyph className="size-full" strokeWidth={1.75} aria-hidden="true" focusable="false" />
    </span>
  );
}
