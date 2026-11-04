import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { Box, Button, CircularProgress, Stack, Tooltip, Typography } from '@mui/material';
import Print from '@mui/icons-material/Print';
import { ShelfMapCabinet, ShelfMapItem, ShelfMapRoom, getShelfMap } from '../api/client';
import { lookupStyle } from '../components/statusStyle';
import { isRtlText } from '../util/rtl';

/** A resource as a book spine: its title written up the spine, coloured by its type. */
function Spine({ item }: { item: ShelfMapItem }) {
  const navigate = useNavigate();
  const style = lookupStyle(item.typeStyle ? { id: 0, name: item.typeName ?? '', style: item.typeStyle } : undefined);
  const title = item.title || '(untitled)';
  return (
    <Tooltip title={`${title}${item.author ? ` — ${item.author}` : ''}${item.typeName ? ` · ${item.typeName}` : ''}`}>
      <Box
        onClick={() => navigate(`/resources?select=${item.id}`)}
        dir={isRtlText(title) ? 'rtl' : 'ltr'}
        sx={{
          writingMode: 'vertical-rl',
          transform: isRtlText(title) ? undefined : 'rotate(180deg)',
          height: 112,
          width: 26,
          px: '2px',
          py: 0.75,
          fontSize: 11,
          lineHeight: 1.1,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          border: 1,
          borderColor: 'rgba(0,0,0,0.25)',
          borderRadius: '2px',
          bgcolor: style.backgroundColor ?? 'grey.200',
          color: style.color ?? 'rgba(0,0,0,0.85)',
          cursor: 'pointer',
          flexShrink: 0,
          opacity: item.isRead ? 0.75 : 1,
          '&:hover': { outline: '2px solid', outlineColor: 'primary.main' },
          printColorAdjust: 'exact',
          WebkitPrintColorAdjust: 'exact',
        }}
      >
        {title}
      </Box>
    </Tooltip>
  );
}

/** One shelf: its spines standing on a plank, with the count. */
function ShelfRow({ name, items, faint }: { name: string; items: ShelfMapItem[]; faint?: boolean }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.25 }}>
        <Typography variant="caption" sx={{ fontWeight: 700, fontStyle: faint ? 'italic' : undefined }}>
          {name}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {items.length}
        </Typography>
      </Stack>
      <Box
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'flex-end',
          gap: '2px',
          minHeight: 40,
          px: 0.5,
          pt: 0.5,
          borderBottom: '5px solid',
          borderColor: '#8d6e63',
          printColorAdjust: 'exact',
          WebkitPrintColorAdjust: 'exact',
        }}
      >
        {items.length === 0 ? (
          <Typography variant="caption" color="text.disabled" sx={{ alignSelf: 'center' }}>
            empty
          </Typography>
        ) : (
          items.map((it) => <Spine key={it.id} item={it} />)
        )}
      </Box>
    </Box>
  );
}

/** A cabinet as a piece of furniture: a frame holding its shelves, top to bottom. */
function CabinetBox({ cabinet }: { cabinet: ShelfMapCabinet }) {
  const count = cabinet.shelves.reduce((n, s) => n + s.items.length, 0) + cabinet.loose.length;
  return (
    <Box
      sx={{
        border: '3px solid #6d4c41',
        borderRadius: 1,
        p: 1.25,
        width: { xs: '100%', sm: 360 },
        breakInside: 'avoid',
        pageBreakInside: 'avoid',
        bgcolor: 'background.paper',
        printColorAdjust: 'exact',
        WebkitPrintColorAdjust: 'exact',
      }}
    >
      <Stack direction="row" justifyContent="space-between" sx={{ mb: 1 }}>
        <Typography sx={{ fontWeight: 700 }}>{cabinet.name ?? 'Shelves in no cabinet'}</Typography>
        <Typography variant="body2" color="text.secondary">
          {count} item{count === 1 ? '' : 's'}
        </Typography>
      </Stack>
      {cabinet.shelves.map((s) => (
        <ShelfRow key={s.id ?? 'x'} name={s.name ?? ''} items={s.items} />
      ))}
      {cabinet.loose.length > 0 && <ShelfRow name="In the cabinet, no shelf given" items={cabinet.loose} faint />}
      {cabinet.shelves.length === 0 && cabinet.loose.length === 0 && (
        <Typography variant="caption" color="text.disabled">
          No shelves yet — add them on the Parameters page.
        </Typography>
      )}
    </Box>
  );
}

function RoomSection({ room }: { room: ShelfMapRoom }) {
  const count =
    room.loose.length + room.cabinets.reduce((n, c) => n + c.loose.length + c.shelves.reduce((m, s) => m + s.items.length, 0), 0);
  return (
    <Box component="section" sx={{ mb: 4 }}>
      <Typography variant="h6" sx={{ fontWeight: 700, borderBottom: 2, borderColor: 'divider', mb: 1.5 }}>
        {room.name ?? 'Not in any room'}{' '}
        <Typography component="span" variant="body2" color="text.secondary">
          · {room.cabinets.filter((c) => c.id != null).length} cabinet(s) · {count} item(s)
        </Typography>
      </Typography>
      <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap alignItems="flex-start">
        {room.cabinets.map((c) => (
          <CabinetBox key={c.id ?? 'none'} cabinet={c} />
        ))}
      </Stack>
      {room.loose.length > 0 && (
        <Box sx={{ mt: 1.5, maxWidth: 760 }}>
          <ShelfRow name="In the room, no cabinet given" items={room.loose} faint />
        </Box>
      )}
    </Box>
  );
}

/**
 * The shelf map report: each room with its cabinets drawn as furniture, their shelves as planks,
 * and the resources on them as spines (coloured by type, title up the spine; click to open). Laid
 * out to print — a cabinet never splits across pages.
 */
export function ShelfMapPage() {
  const { data, isLoading } = useQuery({ queryKey: ['shelf-map'], queryFn: getShelfMap });
  if (isLoading || !data) {
    return (
      <Box sx={{ p: 6, display: 'flex', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    );
  }
  return (
    <Box sx={{ p: { xs: 2, sm: 4 }, maxWidth: 1200, mx: 'auto' }}>
      <Stack className="no-print" direction="row" spacing={1} sx={{ mb: 2, '@media print': { display: 'none' } }}>
        <Button variant="contained" startIcon={<Print />} onClick={() => window.print()}>
          Print
        </Button>
        <Button component={Link} to="/reports">
          Back to reports
        </Button>
      </Stack>
      <Box component="header" sx={{ mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          Shelf map
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {data.placed} resource(s) placed · {data.unplaced} without a place · {new Date().toLocaleDateString()}
        </Typography>
      </Box>
      {data.rooms.length === 0 ? (
        <Typography color="text.secondary">
          No rooms yet. Add rooms, cabinets and shelves on the Parameters page, then set “Where it is” on resources.
        </Typography>
      ) : (
        data.rooms.map((r) => <RoomSection key={r.id ?? 'none'} room={r} />)
      )}
    </Box>
  );
}
