#!/bin/sh

#
# Starts WakeMeApp. Any argument is passed on to the application:
#
#   wakemeapp.sh                              starts in the tray (set the calendar in the settings dialog)
#   wakemeapp.sh --calendar <link or file>    reads that calendar for this run
#   wakemeapp.sh --help                       lists the options
#
# JAVA_HOME is used if it points to a JDK/JRE (Java 21 or later), else the java in the path.
# JAVA_OPTS adds options for the JVM; conf/wakemeapp.conf, if it exists, is read before starting (it may set
# JAVA_OPTS or JAVA_HOME).
#

# resolving links: this script may be started through a symbolic link, e.g. /usr/local/bin/wakemeapp
prg="$0"
while [ -h "$prg" ]
do
  target=$(readlink "$prg")
  case "$target" in
    /*) prg="$target" ;;
    *)  prg="$(dirname "$prg")/$target" ;;
  esac
done

scriptdir=$(cd "$(dirname "$prg")" && pwd)
home=$(dirname "$scriptdir")
scriptname=wakemeapp

. "$scriptdir/scriptutils"

load_config "$home/conf" "$scriptname"
set_java
set_classpath "$home/lib" "wakemeapp-"

#
# JavaFX is on the module path, everything else on the class path. The working directory is not changed.
# The paths are quoted arguments, so an installation directory with spaces works; $JAVA_OPTS, which the user
# writes, is split on its spaces on purpose.
#
# shellcheck disable=SC2086
exec "$javacmd" \
    "-Djava.util.logging.config.file=$home/conf/logging.properties" \
    --enable-native-access=ALL-UNNAMED \
    --module-path "$home/lib/modules" \
    --add-modules=javafx.controls,javafx.fxml,javafx.web,javafx.media \
    $JAVA_OPTS \
    -cp "$classpath" \
    ste.wakemeapp.WakeMeApp "$@"
