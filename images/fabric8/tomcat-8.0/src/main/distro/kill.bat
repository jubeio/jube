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

rem
rem Discover the APP_BASE from the location of this script.
rem

setlocal

if "%APP_BASE%" == "" (
  set APP_BASE=%CD%
)

call %APP_BASE%\env.bat

set PID_FILE=process.pid

if not exist %PID_FILE% (
  echo Tomcat is stopped
  goto :END
)

set /p PID_TMP=<%PID_FILE%
if not "%PID_TMP%" == "" (
  set PID=%PID_TMP%
  for /F "tokens=2 delims= " %%A in ('TASKLIST /FI "PID eq %PID_TMP%" /NH') do set PID_STATUS=%%A
) else (
  set PID_STATUS=No
)

if "%PID_STATUS%" == "No" (
  echo Tomcat is stopped
) else (
  taskkill /F /PID %PID%
)


