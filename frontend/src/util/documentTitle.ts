/**
 * The browser tab title, from three sources in order: what the page on screen is showing (a
 * record's summary, a dashboard's name), else the route's name (Calendar, Tasks), else the app
 * name. Kept outside React because a child's effect runs before its parent's, so a route-level
 * effect writing document.title directly would overwrite the page's own title on first render.
 */
const pageTitles: { title: string }[] = [];
let routeTitle: string | undefined;
let appName: string | undefined;

function apply() {
  const title = pageTitles[pageTitles.length - 1]?.title ?? routeTitle ?? appName;
  if (title) document.title = title;
}

export function setRouteTitle(title: string | undefined) {
  routeTitle = title;
  apply();
}

export function setAppTitle(name: string) {
  appName = name;
  apply();
}

/** Adds a page's title; the returned function takes it away again. The latest one shows. */
export function pushPageTitle(title: string): () => void {
  const entry = { title };
  pageTitles.push(entry);
  apply();
  return () => {
    pageTitles.splice(pageTitles.indexOf(entry), 1);
    apply();
  };
}
