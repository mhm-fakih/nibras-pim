#!/usr/bin/env bash
# Launch the audio player, checking its GTK + GStreamer deps first with a helpful message.
set -e
cd "$(dirname "$0")"

if ! python3 -c "import gi; gi.require_version('Gtk','3.0'); gi.require_version('Gst','1.0')" 2>/dev/null; then
    cat >&2 <<'EOF'
Missing PyGObject / GTK 3 / GStreamer bindings. Install them:

  Debian/Ubuntu: sudo apt install python3-gi gir1.2-gtk-3.0 gir1.2-gst-1.0 \
                   gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly
  Fedora:        sudo dnf install python3-gobject gtk3 gstreamer1 \
                   gstreamer1-plugins-good gstreamer1-plugins-bad-free gstreamer1-plugins-ugly-free
  Arch:          sudo pacman -S python-gobject gtk3 gstreamer gst-plugins-good gst-plugins-bad gst-plugins-ugly
EOF
    exit 1
fi

exec python3 player.py "$@"
