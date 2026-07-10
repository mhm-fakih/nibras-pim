import { SvgIcon, SvgIconProps } from '@mui/material';

/**
 * Symbol for the app's idea: a page split diagonally — a calendar (dots, by time) in the upper-left,
 * notebook rows (by type) in the lower-right. Uses currentColor so it tints with the surrounding text.
 */
export function CalendarNotebookIcon(props: SvgIconProps) {
  return (
    <SvgIcon viewBox="0 0 24 24" {...props}>
      <g
        fill="none"
        stroke="currentColor"
        strokeWidth={1.6}
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        {/* page frame + calendar binding tabs */}
        <rect x="3" y="4.5" width="18" height="16" rx="2.2" />
        <path d="M7.5 3 V6" />
        <path d="M11.5 3 V6" />
        {/* diagonal split (top-right → bottom-left) */}
        <path d="M20.3 5.2 L3.7 19.8" />
        {/* notebook rows (lower-right, by type) */}
        <path d="M12.5 13 H18.5" />
        <path d="M10.5 15.6 H18.5" />
        <path d="M8.6 18.1 H18.5" />
      </g>
      {/* calendar dots (upper-left, by time) */}
      <g fill="currentColor">
        <circle cx="7" cy="9.6" r="1" />
        <circle cx="10.2" cy="9.6" r="1" />
        <circle cx="7" cy="12.6" r="1" />
        <circle cx="10.2" cy="12.6" r="1" />
      </g>
    </SvgIcon>
  );
}
