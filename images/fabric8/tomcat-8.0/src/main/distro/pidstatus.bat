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

rem to get the status of a pid

rem clear variables first
set PID_STATUS=No
set PID=

if not exist %PID_FILE% (
  goto :END
)

set /p PID_TMP=<%PID_FILE%
if not "%PID_TMP%" == "" (
  set PID=%PID_TMP%
  for /F "tokens=2 delims= " %%A in ('TASKLIST /FI "PID eq %PID_TMP%" /NH') do set PID_STATUS=%%A
) else (
  set PID=
)
set PID_TMP=

:END
