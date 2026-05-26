#!/usr/bin/env bash

# To use the GUI, you must set the environment variable HF_TOKEN. .

PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
PRGDIR=`dirname "$PRG"`

# Only set LGT_HOME if not already set
[ -z "$LGT_HOME" ] && LGT_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

CLASS_PATH=$LGT_HOME/resources/:$LGT_HOME/lib/*:$LGT_HOME/config/
LOG4J_PARM=-Dlog4j.configuration=file:$LGT_HOME/config/log4j.xml

GUI_RUNNER=org.apache.ctakes.gui.pipeline.PiperRunnerGui
PIPER_FILE=resources/pipeline/LangGraphTimelines.piper

echo "Usage: runLGT-Gui -i {inputDir} -o {outputDir}"

cd $LGT_HOME

java -cp $CLASS_PATH $LOG4J_PARM -Xms512M -Xmx3g $GUI_RUNNER -p $PIPER_FILE "$@"
# rather than check uname and try to account for emulators etc., just check for failure and retry as cygwin.
#if [ $? != 0 ]; then
#   CLASS_PATH=`cygpath -pw $CLASS_PATH`
#   LOG4J_PARM=-Dlog4j.configuration=file:`cygpath -w $LGT_HOME`/resources/log4j.xml
#   java -cp $CLASS_PATH -Xms512M -Xmx3g $GUI_RUNNER -p $PIPER_FILE "$@"
#fi