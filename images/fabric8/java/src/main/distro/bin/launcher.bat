@echo off

rem  Copyright 2005-2014 Red Hat, Inc.
rem
rem  Red Hat licenses this file to you under the Apache License, version
rem  2.0 (the "License"); you may not use this file except in compliance
rem  with the License.  You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem  Unless required by applicable law or agreed to in writing, software
rem  distributed under the License is distributed on an "AS IS" BASIS,
rem  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
rem  implied.  See the License for the specific language governing
rem  permissions and limitations under the License.
rem

rem Helper functions

rem
rem Discover the APP_BASE from the location of this script.
rem
if "%APP_BASE%" == "" (
  set APP_BASE=%CD%
)

echo APP_BASE is: %APP_BASE%

set APP_USER=""
set SERVICE="process"

set SERVICE_NAME=%RUNTIME_ID%
if "%SERVICE_NAME%" == "" (
  set SERVICE_NAME=%SERVICE%
)

call %APP_BASE%\env.bat

set PID_FILE=%APP_BASE%\process.pid

rem Redirect process output to log files
set APP_CONSOLE_CMD=%APP_BASE%\logs\cmd.log
set APP_CONSOLE_OUT=%APP_BASE%\logs\out.log
set APP_CONSOLE_ERR=%APP_BASE%\logs\err.log

rem Add the files in the maven dir using wildcard style
if "%CLASSPATH%" == "" (
  set CLASSPATH=%APP_BASE%\maven\*;classes
) else (
  set CLASSPATH=%CLASSPATH%;%APP_BASE%\maven\*;classes
)

rem get current dir name only (which is a bit odd way to do in windows bat)
rem which we will use as agent id (as the dirname is unique)
for %%a in (.) do set APP_BASENAME=%%~na

if not "%JOLOKIA_PORT%" == "" (
  if "%AGENT_ID%" == "" (
    set AGENT_ID=%APP_BASENAME%
  )
  set JOLOKIA_ARGS=-javaagent:%APP_BASE%jolokia-agent.jar=host=0.0.0.0,port=%JOLOKIA_PORT%,agentId=%AGENT_ID%
)

rem set JVM_DEBUG_ARGS="%JVM_DEBUG_ARGS%"
if "%JVM_DEBUG_ARGS%" == "" (
  if "%JVM_DEBUG%" == "TRUE" (
    set JVM_DEBUG_ARGS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
  )
)

set JVM_EXEC=java
rem TODO
rem set JVM_ARGS=jvmArgs
set JVM_ARGS=%JVM_ARGS%
set APP_ARGS=%MAIN_ARGS%
set MAIN_JAR=%APP_BASE%\maven\main.jar
if "%MAIN%" == "" (
  set MAIN=%MAIN%
)

if not "%JOLOKIA_ARGS%" == "" (
  if "%JAVA_AGENT%" == "" (
    set JAVA_AGENT=%JOLOKIA_ARGS%
  ) else (
    set JAVA_AGENT=%JAVA_AGENT% %JOLOKIA_ARGS%
  )
)
rem set SYSTEM_PROPERTIES="%SYSTEM_PROPERTIES%"
rem set STOP_TIMEOUT="%PROCESS_STOP_TIMEOUT%"
if "%STOP_TIMEOUT%" == "" (
  set STOP_TIMEOUT=30
)

if "%1" == "" goto :USAGE
if "%1" == "run" goto :RUN
if "%1" == "start" goto :START
if "%1" == "status" goto :STATUS
if "%1" == "stop" goto :STOP
if "%1" == "force-stop" goto :FORCE_STOP
goto :USAGE

:RUN
  echo "Running %SERVICE_NAME%"

  call %APP_BASE%\bin\pidstatus.bat
  if NOT "%PID_STATUS%" == "No" (
    echo Already running
    goto :END1
  )

  set RUN_COMMAND=%JVM_EXEC%
  if not "%JVM_DEBUG_ARGS%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% %JVM_DEBUG_ARGS%
  )
  if not "%JAVA_AGENT%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% %JAVA_AGENT%
  )
  if not "%JVM_ARGS%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% %JVM_ARGS%
  )
  if not "%SYSTEM_PROPERTIES%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% %SYSTEM_PROPERTIES%
  )
  set RUN_COMMAND=%RUN_COMMAND% -classpath %CLASSPATH%
  if "%MAIN%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% -jar %MAIN_JAR%
  ) else (
    set RUN_COMMAND=%RUN_COMMAND% %MAIN%
  )
  if not "%APP_ARGS%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% %APP_ARGS%
  )
  echo %RUN_COMMAND%

  echo Running: >> %APP_CONSOLE_CMD%
  echo %RUN_COMMAND% >> %APP_CONSOLE_CMD%
  echo Environment variables: >> %APP_CONSOLE_CMD%
  call set | sort >> %APP_CONSOLE_CMD%

  title %APP_BASENAME%
  echo Running %RUN_COMMAND%
  rem when we run then we do not redirect output but let it print on the console
  %RUN_COMMAND%
  goto :END

:START
  echo "Starting %SERVICE_NAME%"

  call %APP_BASE%\bin\pidstatus.bat
  if not "%PID_STATUS%" == "No" (
    echo Already running
    goto :END1
  )

  set RUN_COMMAND=%JVM_EXEC%
  if not "%JVM_DEBUG_ARGS%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% %JVM_DEBUG_ARGS%
  )
  if not "%JAVA_AGENT%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% %JAVA_AGENT%
  )
  if not "%JVM_ARGS%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% %JVM_ARGS%
  )
  if not "%SYSTEM_PROPERTIES%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% %SYSTEM_PROPERTIES%
  )
  set RUN_COMMAND=%RUN_COMMAND% -classpath %CLASSPATH%
  if "%MAIN%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% -jar %MAIN_JAR%
  ) else (
    set RUN_COMMAND=%RUN_COMMAND% %MAIN%
  )
  if not "%APP_ARGS%" == "" (
    set RUN_COMMAND=%RUN_COMMAND% %APP_ARGS%
  )
  echo %RUN_COMMAND%

  echo Starting: >> %APP_CONSOLE_CMD%
  echo %RUN_COMMAND% >> %APP_CONSOLE_CMD%
  echo Environment variables: >> %APP_CONSOLE_CMD%
  call set | sort >> %APP_CONSOLE_CMD%

  echo Starting %RUN_COMMAND%
  start /min "%APP_BASENAME%" %RUN_COMMAND% 1>> %APP_CONSOLE_OUT% 2>> %APP_CONSOLE_ERR%

  rem see if we got started
  timeout /T 2 > NUL
  for /F "tokens=2 delims= " %%A in ('tasklist /FI "WINDOWTITLE eq %APP_BASENAME%" /NH') do set PID=%%A
  if "%PID%" == "No" (
     echo Could not start %SERVICE_NAME%
     goto :END1
  )
  echo %SERVICE_NAME% is now running: PID=%PID%

  rem write PID to pid file
  echo %PID% > %PID_FILE%

  goto :END

:STOP

  echo Gracefully Stopping %SERVICE_NAME% within %STOP_TIMEOUT% second(s)

  call %APP_BASE%\bin\pidstatus.bat
  if "%PID_STATUS%" == "No" (
    echo Already stopped
    goto :END
  )

  if not "%PID%" == "" (
    taskkill /PID %PID%
  )

  for /L %%L in (1,1,%STOP_TIMEOUT%) do (
    rem cannot call pidstatus in this for loop
    set PID_STOP_STATUS=No
    for /F "tokens=2 delims= " %%A in ('TASKLIST /FI "PID eq %PID%" /NH') do set PID_STOP_STATUS=%%A
    if "%PID_STOP_STATUS%" == "No" (
      rem delete pid file now its stopped
      if exist %PID_FILE% del %PID_FILE%
      goto :END
    )
    rem sleep for 1 second
    timeout /T 1 /NOBREAK > NUL
  )

  echo Could not gracefully stop %SERVICE_NAME% with PID=%pid% within %STOP_TIMEOUT% second(s)
  goto :END1

:FORCE_STOP

  echo Forcibly stopping %SERVICE_NAME%

  call %APP_BASE%\bin\pidstatus.bat
  if "%PID_STATUS%" == "No" (
    echo Already stopped
    goto :END
  )

  if not "%PID%" == "" (
    taskkill /F /PID %PID%
  )

  call %APP_BASE%\bin\pidstatus.bat
  if "%PID_STATUS%" == "No" (
    rem delete pid file now its stopped
    if exist %PID_FILE% del %PID_FILE%
  )

  if not "%PID_STATUS%" == "No" (
    echo Could not forcibly stop %SERVICE_NAME% with PID=%pid%
    goto :END1
  )

  goto :END

:STATUS

  call %APP_BASE%\bin\pidstatus.bat
  if "%PID_STATUS%" == "No" (
    echo %SERVICE_NAME% is stopped
  ) else (
    echo %SERVICE_NAME% is running: PID=%PID%
  )

  goto :END

:USAGE
  echo "Usage: %0% {start|stop|restart|force-stop|status}"
  goto :END


:END1
set ERROR_CODE=1

:END
endlocal
