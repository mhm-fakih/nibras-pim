import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Checkbox,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Link,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  MenuItem,
  Paper,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import FolderIcon from "@mui/icons-material/Folder";
import ImageIcon from "@mui/icons-material/Image";
import DescriptionIcon from "@mui/icons-material/Description";
import PictureAsPdf from "@mui/icons-material/PictureAsPdf";
import AudioFile from "@mui/icons-material/AudioFile";
import VideoFile from "@mui/icons-material/VideoFile";
import InsertDriveFile from "@mui/icons-material/InsertDriveFile";
import ArrowUpward from "@mui/icons-material/ArrowUpward";
import NavigateBefore from "@mui/icons-material/NavigateBefore";
import NavigateNext from "@mui/icons-material/NavigateNext";
import Refresh from "@mui/icons-material/Refresh";
import AssignmentTurnedIn from "@mui/icons-material/AssignmentTurnedIn";
import {
  BASE,
  SettleEntry,
  attachSettled,
  createEntity,
  discardSettled,
  errorDetail,
  getSettleBlob,
  getSettleListing,
  getSettleText,
  trashSettled,
} from "../api/client";
import { ENTITY_BY_KEY, FieldDef } from "../config/entities";
import { useModules } from "../hooks/useModules";
import { SideNav } from "../components/SideNav";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { dateFromFileName, isoDay } from "../util/fileDate";

/** Modules a file can be settled into (the ones the generic create takes a title and body for). */
const CREATABLE = [
  "resources",
  "notes",
  "tasks",
  "journals",
  "planners",
  "goals",
  "writings",
];
const PREFS_KEY = "nibras.fileProcessor";
/** Previews larger than this are offered as a download link instead of loaded into the page. */
const PREVIEW_LIMIT = 150 * 1024 * 1024;

type FileAction = "move" | "cover" | "leave";

interface Prefs {
  module: string;
  /** Per module: which field the text goes to, and which date field the day goes to. */
  bodyField: Record<string, string>;
  dateField: Record<string, string>;
}

function loadPrefs(): Prefs {
  const fallback: Prefs = { module: "resources", bodyField: {}, dateField: {} };
  try {
    return {
      ...fallback,
      ...JSON.parse(localStorage.getItem(PREFS_KEY) ?? "{}"),
    };
  } catch {
    return fallback;
  }
}

function savePrefs(p: Prefs) {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify(p));
  } catch {
    // Not remembered for next time; this visit still uses it.
  }
}

const titleFieldOf = (fields: FieldDef[]) =>
  fields.find(
    (f) => f.type === "text" && ["title", "summary", "name"].includes(f.name),
  ) ?? fields.find((f) => f.type === "text");
const bodyFieldsOf = (fields: FieldDef[]) =>
  fields.filter((f) => ["markdown", "richtext", "textarea"].includes(f.type));
const dateFieldsOf = (fields: FieldDef[]) =>
  fields.filter((f) => f.type === "date" || f.type === "datetime");

const stripExt = (name: string) => {
  const dot = name.lastIndexOf(".");
  return dot > 0 ? name.slice(0, dot) : name;
};

const humanSize = (n: number) =>
  n < 1024
    ? `${n} B`
    : n < 1024 ** 2
      ? `${(n / 1024).toFixed(0)} KB`
      : `${(n / 1024 ** 2).toFixed(1)} MB`;

function KindIcon({ kind }: { kind: string }) {
  const Icon =
    kind === "folder"
      ? FolderIcon
      : kind === "image"
        ? ImageIcon
        : kind === "text"
          ? DescriptionIcon
          : kind === "pdf"
            ? PictureAsPdf
            : kind === "audio"
              ? AudioFile
              : kind === "video"
                ? VideoFile
                : InsertDriveFile;
  return (
    <Icon fontSize="small" color={kind === "folder" ? "warning" : "action"} />
  );
}

/**
 * Settling a folder one entry at a time (the old app's scan processor): see what is in it, one
 * level at a time; look at a file — an image, a text, a PDF, a recording — and make a record of
 * it: which module, under what title, the text into which field, the day into which date. The
 * file then moves into the record's repo-1 folder (an image optionally as its cover), or aside
 * into DELETED/ when it isn't worth keeping, or to the desktop's trash (after asking). A folder
 * can also be settled whole, as one record: its contents become that record's folder.
 */
export function FileProcessorPage() {
  useDocumentTitle("File processor");
  const qc = useQueryClient();
  const { enabled } = useModules();
  const modules = enabled.filter(
    (m) => CREATABLE.includes(m.moduleKey) && ENTITY_BY_KEY[m.moduleKey],
  );

  const [prefs, setPrefs] = useState<Prefs>(loadPrefs);
  const updatePrefs = (p: Partial<Prefs>) => {
    const next = { ...prefs, ...p };
    setPrefs(next);
    savePrefs(next);
  };

  const [path, setPath] = useState("");
  const [dirDraft, setDirDraft] = useState<string | null>(null);
  const [dirOverride, setDirOverride] = useState<string | undefined>(undefined);
  const [selected, setSelected] = useState<string | null>(null);
  const [done, setDone] = useState<{ text: string; href?: string } | null>(
    null,
  );

  const listing = useQuery({
    queryKey: ["file-settle", path, dirOverride],
    queryFn: () => getSettleListing(path, dirOverride),
  });
  const entries = useMemo(() => listing.data?.entries ?? [], [listing.data]);
  const files = entries.filter((e) => !e.directory);
  const current = entries.find((e) => e.name === selected) ?? null;
  const fullPath = (name: string) => (path ? `${path}/${name}` : name);

  // Where the settled file stood, so the one after it comes up next rather than the first again.
  const settledAt = useRef(0);

  // Open on the first file of a folder, and on the next one when the selected file has gone.
  useEffect(() => {
    if (!listing.data) return;
    if (selected && entries.some((e) => e.name === selected)) return;
    setSelected(
      files[Math.min(settledAt.current, files.length - 1)]?.name ?? null,
    );
    settledAt.current = 0;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listing.data]);

  const open = (sub: string) => {
    setPath(sub);
    setSelected(null);
    setDone(null);
  };

  const step = (by: number) => {
    if (!files.length) return;
    const i = Math.max(
      0,
      files.findIndex((f) => f.name === selected),
    );
    setSelected(files[Math.min(files.length - 1, Math.max(0, i + by))].name);
  };

  const crumbs = path ? path.split("/") : [];

  return (
    <Box sx={{ display: "flex", height: "100vh", overflow: "hidden" }}>
      <SideNav />
      <Box
        sx={{
          p: 2,
          flex: 1,
          minWidth: 0,
          display: "flex",
          flexDirection: "column",
          gap: 1.5,
          minHeight: 0,
        }}
      >
        <Stack
          direction="row"
          spacing={1}
          alignItems="center"
          flexWrap="wrap"
          useFlexGap
        >
          <Typography variant="h6" sx={{ mr: 1 }}>
            File processor
          </Typography>
          <TextField
            size="small"
            label="Folder to settle"
            value={dirDraft ?? listing.data?.dir ?? ""}
            onChange={(e) => setDirDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && dirDraft) {
                setDirOverride(dirDraft);
                setDirDraft(null);
                open("");
              }
            }}
            helperText={dirDraft ? "Enter to open it" : undefined}
            sx={{ minWidth: 320 }}
          />
          <Tooltip title="Read the folder again">
            <IconButton onClick={() => listing.refetch()}>
              <Refresh />
            </IconButton>
          </Tooltip>
        </Stack>

        {listing.isError && (
          <Alert severity="error">
            {errorDetail(listing.error, "Could not read the folder.")}
          </Alert>
        )}

        <Box
          sx={{
            display: "flex",
            gap: 2,
            flex: 1,
            minHeight: 0,
            flexWrap: { xs: "wrap", md: "nowrap" },
          }}
        >
          {/* The folder, one level */}
          <Paper
            variant="outlined"
            sx={{
              width: { xs: "100%", md: 280 },
              flexShrink: 0,
              display: "flex",
              flexDirection: "column",
              minHeight: 0,
            }}
          >
            <Stack
              direction="row"
              alignItems="center"
              sx={{ px: 1, py: 0.5, borderBottom: 1, borderColor: "divider" }}
            >
              <IconButton
                size="small"
                disabled={!path}
                onClick={() => open(crumbs.slice(0, -1).join("/"))}
              >
                <ArrowUpward fontSize="small" />
              </IconButton>
              <Breadcrumbs maxItems={3} sx={{ fontSize: 13, ml: 0.5 }}>
                <Link
                  component="button"
                  underline="hover"
                  onClick={() => open("")}
                >
                  top
                </Link>
                {crumbs.map((c, i) => (
                  <Link
                    key={i}
                    component="button"
                    underline="hover"
                    onClick={() => open(crumbs.slice(0, i + 1).join("/"))}
                  >
                    {c}
                  </Link>
                ))}
              </Breadcrumbs>
            </Stack>
            {listing.isLoading && <CircularProgress size={20} sx={{ m: 2 }} />}
            {listing.data && entries.length === 0 && (
              <Typography color="text.secondary" sx={{ p: 2 }}>
                Nothing left here — settled.
              </Typography>
            )}
            <List dense sx={{ overflow: "auto", flex: 1 }}>
              {entries.map((e) => (
                <ListItemButton
                  key={e.name}
                  selected={e.name === selected}
                  onClick={() =>
                    e.directory
                      ? open(fullPath(e.name))
                      : (setSelected(e.name), setDone(null))
                  }
                  title={e.directory ? "Open the folder" : undefined}
                >
                  <ListItemIcon sx={{ minWidth: 30 }}>
                    <KindIcon kind={e.kind} />
                  </ListItemIcon>
                  <ListItemText
                    primary={e.name}
                    secondary={e.directory ? undefined : humanSize(e.size)}
                    primaryTypographyProps={{
                      noWrap: true,
                      dir: "auto",
                      fontSize: 13,
                    }}
                  />
                  {e.directory && (
                    <Tooltip title="Settle the whole folder as one record">
                      <IconButton
                        size="small"
                        edge="end"
                        onClick={(ev) => {
                          ev.stopPropagation();
                          setSelected(e.name);
                          setDone(null);
                        }}
                      >
                        <AssignmentTurnedIn fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                </ListItemButton>
              ))}
            </List>
          </Paper>

          {/* One file: what it is, and the record to make of it */}
          <Box
            sx={{
              flex: 1,
              minWidth: 0,
              display: "flex",
              flexDirection: "column",
              gap: 1.5,
              overflow: "auto",
            }}
          >
            {done && (
              <Alert severity="success" onClose={() => setDone(null)}>
                {done.text}{" "}
                {done.href && (
                  <Link href={done.href} target="_blank" rel="noopener">
                    Open the record
                  </Link>
                )}
              </Alert>
            )}
            {!current ? (
              <Typography color="text.secondary" sx={{ p: 2 }}>
                {entries.length ? "Pick a file, or open a folder." : ""}
              </Typography>
            ) : (
              <>
                <Stack
                  direction="row"
                  alignItems="center"
                  spacing={1}
                  flexWrap="wrap"
                  useFlexGap
                >
                  {current.directory ? (
                    <FolderIcon color="warning" />
                  ) : (
                    <>
                      <IconButton
                        size="small"
                        onClick={() => step(-1)}
                        disabled={files[0]?.name === current.name}
                      >
                        <NavigateBefore />
                      </IconButton>
                      <Typography variant="caption" color="text.secondary">
                        {files.findIndex((f) => f.name === current.name) + 1} /{" "}
                        {files.length}
                      </Typography>
                      <IconButton
                        size="small"
                        onClick={() => step(1)}
                        disabled={
                          files[files.length - 1]?.name === current.name
                        }
                      >
                        <NavigateNext />
                      </IconButton>
                    </>
                  )}
                  <Typography
                    sx={{ fontWeight: 600, wordBreak: "break-all" }}
                    dir="auto"
                  >
                    {current.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {current.directory
                      ? "folder, as one record"
                      : humanSize(current.size)}
                    {current.modified &&
                      ` · modified ${new Date(current.modified).toLocaleString()}`}
                  </Typography>
                </Stack>
                <Box
                  sx={{
                    display: "flex",
                    gap: 2,
                    flexWrap: { xs: "wrap", lg: "nowrap" },
                    alignItems: "flex-start",
                  }}
                >
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Preview
                      key={fullPath(current.name)}
                      entry={current}
                      path={fullPath(current.name)}
                    />
                  </Box>
                  <SettleForm
                    key={fullPath(current.name)}
                    entry={current}
                    path={fullPath(current.name)}
                    modules={modules.map((m) => m.moduleKey)}
                    prefs={prefs}
                    onPrefs={updatePrefs}
                    onSettled={(text, href) => {
                      settledAt.current = Math.max(
                        0,
                        files.findIndex((f) => f.name === current.name),
                      );
                      setDone({ text, href });
                      qc.invalidateQueries({ queryKey: ["file-settle"] });
                    }}
                    onSkip={() =>
                      current.directory
                        ? setSelected(files[0]?.name ?? null)
                        : step(1)
                    }
                  />
                </Box>
              </>
            )}
          </Box>
        </Box>
      </Box>
    </Box>
  );
}

/** The file as it is: an image, a text, a PDF, a recording; anything else by name only. */
function Preview({ entry, path }: { entry: SettleEntry; path: string }) {
  if (entry.directory) return <FolderPreview path={path} />;
  return <FilePreview entry={entry} path={path} />;
}

/** A folder settled whole: what will move into the record's folder. */
function FolderPreview({ path }: { path: string }) {
  const inner = useQuery({
    queryKey: ["file-settle", path, undefined],
    queryFn: () => getSettleListing(path),
  });
  if (inner.isLoading) return <CircularProgress size={24} />;
  if (inner.isError)
    return (
      <Alert severity="error">
        {errorDetail(inner.error, "Could not read the folder.")}
      </Alert>
    );
  const list = inner.data?.entries ?? [];
  return (
    <Paper variant="outlined" sx={{ maxHeight: "70vh", overflow: "auto" }}>
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{ px: 2, pt: 1, display: "block" }}
      >
        {list.length} entr{list.length === 1 ? "y" : "ies"} — all of it becomes
        the record’s folder
      </Typography>
      <List dense>
        {list.map((e) => (
          <ListItemText
            key={e.name}
            sx={{ px: 2, display: "flex", alignItems: "center", gap: 1 }}
            primary={
              <Stack direction="row" spacing={1} alignItems="center">
                <KindIcon kind={e.kind} />
                <span dir="auto">{e.name}</span>
                <Typography variant="caption" color="text.secondary">
                  {e.directory ? "" : humanSize(e.size)}
                </Typography>
              </Stack>
            }
          />
        ))}
      </List>
    </Paper>
  );
}

function FilePreview({ entry, path }: { entry: SettleEntry; path: string }) {
  const tooBig = entry.size > PREVIEW_LIMIT;
  const wantsBlob =
    ["image", "pdf", "audio", "video"].includes(entry.kind) && !tooBig;
  const text = useQuery({
    queryKey: ["file-settle-text", path],
    queryFn: () => getSettleText(path),
    enabled: entry.kind === "text",
  });
  const blob = useQuery({
    queryKey: ["file-settle-blob", path],
    queryFn: () => getSettleBlob(path),
    enabled: wantsBlob,
    gcTime: 0,
  });
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!blob.data) return;
    const u = URL.createObjectURL(blob.data);
    setUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [blob.data]);

  if (text.isLoading || blob.isLoading) return <CircularProgress size={24} />;
  if (text.isError || blob.isError)
    return (
      <Alert severity="error">
        {errorDetail(text.error ?? blob.error, "Could not read the file.")}
      </Alert>
    );

  if (entry.kind === "text" && text.data) {
    return (
      <Paper
        variant="outlined"
        sx={{ p: 2, maxHeight: "70vh", overflow: "auto" }}
      >
        {text.data.truncated && (
          <Alert severity="warning" sx={{ mb: 1 }}>
            Only the first 5 MB are shown and taken.
          </Alert>
        )}
        <Box
          dir="auto"
          sx={{
            whiteSpace: "pre-wrap",
            fontSize: 14,
            lineHeight: 1.7,
            unicodeBidi: "plaintext",
            textAlign: "start",
          }}
        >
          {text.data.text}
        </Box>
      </Paper>
    );
  }
  if (url && entry.kind === "image") {
    return (
      <Box
        component="img"
        src={url}
        alt={entry.name}
        sx={{
          maxWidth: "100%",
          maxHeight: "75vh",
          objectFit: "contain",
          display: "block",
        }}
      />
    );
  }
  if (url && entry.kind === "pdf") {
    return (
      <Box
        component="iframe"
        src={url}
        title={entry.name}
        sx={{
          width: "100%",
          height: "75vh",
          border: 1,
          borderColor: "divider",
        }}
      />
    );
  }
  if (url && entry.kind === "audio")
    return <audio src={url} controls style={{ width: "100%" }} />;
  if (url && entry.kind === "video")
    return (
      <Box
        component="video"
        src={url}
        controls
        sx={{ maxWidth: "100%", maxHeight: "75vh" }}
      />
    );
  return (
    <Typography color="text.secondary">
      {tooBig ? "Too large to show here." : "No preview for this kind of file."}
    </Typography>
  );
}

/** The record to make of the file, and what then happens to the file. */
function SettleForm({
  entry,
  path,
  modules,
  prefs,
  onPrefs,
  onSettled,
  onSkip,
}: {
  entry: SettleEntry;
  path: string;
  modules: string[];
  prefs: Prefs;
  onPrefs: (p: Partial<Prefs>) => void;
  onSettled: (message: string, href?: string) => void;
  onSkip: () => void;
}) {
  const module = modules.includes(prefs.module)
    ? prefs.module
    : (modules[0] ?? "resources");
  const config = ENTITY_BY_KEY[module];
  const fields = config?.fields ?? [];
  const titleField = titleFieldOf(fields);
  const bodyFields = bodyFieldsOf(fields);
  const dateFields = dateFieldsOf(fields);

  const defaultBody =
    prefs.bodyField[module] ??
    (bodyFields.some((f) => f.name === "fullText")
      ? "fullText"
      : "description");
  const bodyField =
    defaultBody === "" || bodyFields.some((f) => f.name === defaultBody)
      ? defaultBody
      : "";
  const defaultDate =
    prefs.dateField[module] ??
    (dateFields.some((f) => f.name === "publishedOn") ? "publishedOn" : "");
  const dateField =
    defaultDate === "" || dateFields.some((f) => f.name === defaultDate)
      ? defaultDate
      : "";

  const isDir = entry.directory;
  // A folder settled whole may take its text from one of the text files in it.
  const inner = useQuery({
    queryKey: ["file-settle", path, undefined],
    queryFn: () => getSettleListing(path),
    enabled: isDir,
  });
  const innerTexts = (inner.data?.entries ?? []).filter(
    (e) => e.kind === "text",
  );
  const [textFrom, setTextFrom] = useState("");
  const textSource = isDir ? (textFrom ? `${path}/${textFrom}` : "") : path;
  const hasText = isDir ? innerTexts.length > 0 : entry.kind === "text";

  const fromName = dateFromFileName(entry.name);
  const [title, setTitle] = useState(isDir ? entry.name : stripExt(entry.name));
  const [day, setDay] = useState(
    fromName ?? (entry.modified ? isoDay(new Date(entry.modified)) : ""),
  );
  const [takeText, setTakeText] = useState(entry.kind === "text");
  const [confirmTrash, setConfirmTrash] = useState(false);
  const [action, setAction] = useState<FileAction>("move");
  const [error, setError] = useState<string | null>(null);

  const settle = useMutation({
    mutationFn: async () => {
      if (!titleField) throw new Error("This module has no title field.");
      const body: Record<string, unknown> = { [titleField.name]: title.trim() };
      if (takeText && bodyField && hasText && textSource) {
        body[bodyField] = (await getSettleText(textSource)).text;
      }
      if (dateField && day) body[dateField] = `${day}T00:00:00`;
      const record = await createEntity(module, body);
      const id = Number(record.id);
      if (action === "leave") return { id, movedTo: null };
      try {
        const r = await attachSettled({
          path,
          module,
          id,
          asCover: action === "cover",
        });
        return { id, movedTo: r.movedTo };
      } catch (e) {
        // The record stands; say so, so it isn't made twice.
        throw new Error(
          `Record #${id} was created, but the file was not moved: ${errorDetail(e, "unknown error")}`,
        );
      }
    },
    onSuccess: ({ id, movedTo }) =>
      onSettled(
        `${config?.label ?? module} #${id} “${title.trim()}” created${movedTo ? `; ${isDir ? "its contents are now in" : "the file is now"} ${movedTo}` : ""}.`,
        `${BASE}read/${module}/${id}`,
      ),
    onError: (e) =>
      setError(
        e instanceof Error && !("isAxiosError" in e)
          ? e.message
          : errorDetail(e, "Could not create the record."),
      ),
  });

  const discard = useMutation({
    mutationFn: () => discardSettled(path),
    onSuccess: (r) => onSettled(`Put aside: ${r.movedTo}`),
    onError: (e) => setError(errorDetail(e, "Could not put it aside.")),
  });

  const trash = useMutation({
    mutationFn: () => trashSettled(path),
    onSuccess: (r) => {
      setConfirmTrash(false);
      onSettled(`In the trash: ${r.movedTo}`);
    },
    onError: (e) => {
      setConfirmTrash(false);
      setError(errorDetail(e, "Could not move it to the trash."));
    },
  });

  const busy = settle.isPending || discard.isPending || trash.isPending;

  return (
    <Paper
      variant="outlined"
      sx={{ p: 2, width: { xs: "100%", lg: 360 }, flexShrink: 0 }}
    >
      <Stack spacing={2}>
        <TextField
          select
          size="small"
          label="Module"
          value={module}
          onChange={(e) => onPrefs({ module: e.target.value })}
        >
          {modules.map((m) => (
            <MenuItem key={m} value={m}>
              {ENTITY_BY_KEY[m]?.label ?? m}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          size="small"
          label={titleField?.label ?? "Title"}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          inputProps={{ dir: "auto" }}
          helperText={`The ${isDir ? "folder" : "file"} name, unless you change it`}
          multiline
        />
        {isDir && innerTexts.length > 0 && (
          <TextField
            select
            size="small"
            label="Text from"
            value={textFrom}
            onChange={(e) => {
              setTextFrom(e.target.value);
              setTakeText(!!e.target.value);
            }}
          >
            <MenuItem value="">
              <em>None of its files</em>
            </MenuItem>
            {innerTexts.map((f) => (
              <MenuItem key={f.name} value={f.name} dir="auto">
                {f.name}
              </MenuItem>
            ))}
          </TextField>
        )}
        {hasText && (!isDir || textFrom) && (
          <Stack direction="row" spacing={1} alignItems="center">
            <FormControlLabel
              control={
                <Checkbox
                  checked={takeText}
                  onChange={(e) => setTakeText(e.target.checked)}
                />
              }
              label="Its text into"
              sx={{ mr: 0 }}
            />
            <TextField
              select
              size="small"
              value={bodyField}
              disabled={!takeText}
              onChange={(e) =>
                onPrefs({
                  bodyField: { ...prefs.bodyField, [module]: e.target.value },
                })
              }
              sx={{ flex: 1 }}
            >
              {bodyFields.map((f) => (
                <MenuItem key={f.name} value={f.name}>
                  {f.label}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        )}
        {dateFields.length > 0 && (
          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              type="date"
              label="Day"
              value={day}
              onChange={(e) => setDay(e.target.value)}
              InputLabelProps={{ shrink: true }}
              helperText={
                fromName && day === fromName
                  ? `From the ${isDir ? "folder" : "file"} name`
                  : day
                    ? `The ${isDir ? "folder" : "file"}’s modified day`
                    : " "
              }
              sx={{ width: 170 }}
            />
            <TextField
              select
              size="small"
              label="Goes to"
              value={dateField}
              onChange={(e) =>
                onPrefs({
                  dateField: { ...prefs.dateField, [module]: e.target.value },
                })
              }
              sx={{ flex: 1 }}
            >
              <MenuItem value="">
                <em>Nowhere</em>
              </MenuItem>
              {dateFields.map((f) => (
                <MenuItem key={f.name} value={f.name}>
                  {f.label}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        )}
        <Box>
          <Typography variant="caption" color="text.secondary">
            Then the {entry.directory ? "folder" : "file"}
          </Typography>
          <RadioGroup
            value={action}
            onChange={(e) => setAction(e.target.value as FileAction)}
          >
            <FormControlLabel
              value="move"
              control={<Radio size="small" />}
              label={
                isDir
                  ? "its contents become the record’s folder (repo 1)"
                  : "moves into the record’s folder (repo 1)"
              }
            />
            {entry.kind === "image" && (
              <FormControlLabel
                value="cover"
                control={<Radio size="small" />}
                label="becomes the record’s cover (cover.ext)"
              />
            )}
            <FormControlLabel
              value="leave"
              control={<Radio size="small" />}
              label="stays here"
            />
          </RadioGroup>
        </Box>
        {error && (
          <Alert severity="error" onClose={() => setError(null)}>
            {error}
          </Alert>
        )}
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Button
            variant="contained"
            disabled={busy || !title.trim() || !titleField}
            onClick={() => {
              setError(null);
              settle.mutate();
            }}
          >
            Create record
          </Button>
          <Button color="inherit" disabled={busy} onClick={onSkip}>
            Skip
          </Button>
          <Tooltip title="Moves it into DELETED/ of the folder being settled — nothing is deleted">
            <span>
              <Button
                color="warning"
                disabled={busy}
                onClick={() => discard.mutate()}
              >
                Put aside
              </Button>
            </span>
          </Tooltip>
          <Button
            color="error"
            disabled={busy}
            onClick={() => setConfirmTrash(true)}
          >
            Delete
          </Button>
        </Stack>
        <Dialog
          open={confirmTrash}
          onClose={() => !trash.isPending && setConfirmTrash(false)}
        >
          <DialogTitle>Move to the trash?</DialogTitle>
          <DialogContent>
            <DialogContentText>
              <span dir="auto" style={{ fontWeight: 600 }}>
                {entry.name}
              </span>{" "}
              {isDir ? "and everything in it" : ""} goes to the desktop’s trash.
              It can be restored from the file manager’s Trash until the trash
              is emptied.
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button
              onClick={() => setConfirmTrash(false)}
              disabled={trash.isPending}
            >
              Cancel
            </Button>
            <Button
              color="error"
              variant="contained"
              disabled={trash.isPending}
              onClick={() => trash.mutate()}
              autoFocus
            >
              Move to trash
            </Button>
          </DialogActions>
        </Dialog>
      </Stack>
    </Paper>
  );
}
