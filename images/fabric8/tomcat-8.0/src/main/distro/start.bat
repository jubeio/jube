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

rem get current dir name only (which is a bit odd way to do in windows bat)
rem which we will use as agent id (as the dirname is unique)
for %%a in (.) do set APP_BASENAME=%%~na

call %APP_BASE%\env.bat

call %APP_BASE%\deploy.bat

rem use unique name in title so we can find it in the tasklist
set TITLE=%APP_BASENAME%
call %APP_BASE%\bin\catalina.bat start

rem see if we got started
timeout /T 2 > NUL
for /F "tokens=2 delims= " %%A in ('tasklist /FI "WINDOWTITLE eq %APP_BASENAME%" /NH') do set PID=%%A
if "%PID%" == "No" (
   echo Could not start Tomcat
   goto :END1
)
echo Tomcat is now running: PID=%PID%

rem write PID to pid file
echo %PID% > %PID_FILE%

goto :END

:END1
set ERROR_CODE=1

:END

