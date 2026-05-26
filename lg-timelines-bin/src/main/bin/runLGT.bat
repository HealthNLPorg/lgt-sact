::   Starts the Piper File Submitter.
::::
:: Requires JAVA JDK 1.8+
::

@REM Guess LGT_HOME if not defined
set CURRENT_DIR=%cd%
if defined LGT_HOME goto gotHome
set LGT_HOME=%CURRENT_DIR%
if exist "%LGT_HOME%\bin\runLGT-Gui.bat" goto okHome
cd ..
set LGT_HOME=%cd%

:gotHome
if exist "%LGT_HOME%\bin\runLGT-Gui.bat" goto okHome
echo The LGT_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto end

:okHome
@REM use JAVA_HOME if set
if exist "%JAVA_HOME%\bin\java.exe" set PATH=%JAVA_HOME%\bin;%PATH%

echo To use this script you must use the following Parameters (-i, -o, -a, ++hf_token):
echo   InputDirectory (-i)     The directory containing clinical notes.
echo   OutputDirectory (-o)    The directory to which output files should be written.
echo   ArtemisBroker (-a)      The directory to an Apache Artemis broker.
echo   (++hf_token)            The token for your HuggingFace account connected to langgraph-timelines.
echo Example: runLGT -i path\to\myFiles -o put\my\output -a place\with\lgt_broker ++hf_token abc123
echo Note: The parameter ++hf_token, if required (not set in environment), must be the last parameter.

cd "%LGT_HOME%"
set "CLASS_PATH=%LGT_HOME%\resources\;%LGT_HOME%\lib\*;%LGT_HOME%\config\"
set PIPE_RUNNER=org.apache.ctakes.core.pipeline.PiperFileRunner
set PIPER_FILE=resources/pipeline/LangGraphTimelines.piper
java -cp "%CLASS_PATH%" -Xms512M -Xmx3g %PIPE_RUNNER% -p %PIPER_FILE% %*
cd %CURRENT_DIR%

:end