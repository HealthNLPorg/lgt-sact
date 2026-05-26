#!/usr/bin/env bash

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

PIPE_RUNNER=org.apache.ctakes.core.pipeline.PiperFileRunner
PIPER_FILE=resources/pipeline/LangGraphTimelines.piper

echo "To use this script you must use the following Parameters (-i, -o, -a, ++hf_token):"
echo "  InputDirectory (-i)     The directory containing clinical notes."
echo "  OutputDirectory (-o)    The directory to which output files should be written."
echo "  ArtemisBroker (-a)      The directory to an Apache Artemis broker."
echo "  (++hf_token)            The token for your HuggingFace account connected to langgraph-timelines."
echo "Example: runLGT -i path/to/myDocs -o put/my/output -a /var/lib/lgt_broker ++hf_token abc123"
echo "Note: The parameter ++hf_token, if required (not set in environment), must be the last parameter."


cd $LGT_HOME

java -cp $CLASS_PATH -Xms512M -Xmx3g $PIPE_RUNNER -p $PIPER_FILE "$@"

# rather than check uname and try to account for emulators etc., just check for failure and retry as cygwin.
#if [ $? != 0 ]; then
#   CLASS_PATH=`cygpath -pw $CLASS_PATH`
#   java -cp $CLASS_PATH -Xms512M -Xmx3g $PIPE_RUNNER -p $PIPER_FILE "$@"
#fi