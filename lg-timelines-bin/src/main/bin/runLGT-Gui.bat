::   Starts the Piper File Submitter GUI.
::::
:: Requires JAVA JDK 17+
::

:: To use the GUI, you must set the environment variable HF_TOKEN. .

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

cd "%LGT_HOME%"
set "CLASS_PATH=%LGT_HOME%\resources\;%LGT_HOME%\lib\*;%LGT_HOME%\config\"
set PIPE_RUNNER=org.apache.ctakes.gui.pipeline.PiperRunnerGui
set PIPER_FILE=resources/pipeline/LangGraphTimelines.piper
java -cp "%CLASS_PATH%" -Xms512M -Xmx3g %PIPE_RUNNER% -p %PIPER_FILE% %*
cd %CURRENT_DIR%

:end