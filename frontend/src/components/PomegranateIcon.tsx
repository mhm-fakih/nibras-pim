import { SvgIcon, SvgIconProps } from '@mui/material';

/**
 * App mark: a minimal line-art open pomegranate — an outlined body + crown (void inside) with a
 * few seed dots. Fixed dark colours so it reads as a logo.
 */
export function PomegranateIcon(props: SvgIconProps) {
  return (
    <SvgIcon viewBox="0 0 24 24" {...props}>
      <g
        fill="none"
        stroke="#5b0e1a"
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        {/* crown */}
        <path d="M9 4.6 L10.4 2 L12 4 L13.6 2 L15 4.6" />
        {/* body */}
        <circle cx="12" cy="13.8" r="8" />
      </g>
      {/* few seeds */}
      <g fill="#b3122a">
        <circle cx="9.7" cy="13" r="1.15" />
        <circle cx="14.3" cy="13" r="1.15" />
        <circle cx="12" cy="16.3" r="1.15" />
      </g>
    </SvgIcon>
  );
}
