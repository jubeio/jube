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

rem defines the default environment settings

set CATALINA_PID=%APP_BASE%/process.pid

if not defined HTTP_PORT set HTTP_PORT=8080
if not defined SHUTDOWN_PORT set SHUTDOWN_PORT=8005
if not defined JOLOKIA_PORT set JOLOKIA_PORT=8778
if not defined TOMCAT_USERNAME set TOMCAT_USERNAME=admin
if not defined TOMCAT_PASSWORD set TOMCAT_PASSWORD=admin

if "%CATALINA_OPTS%" == "" (
  set CATALINA_OPTS=-DhttpPort=%HTTP_PORT% -DshutdownPort=%SHUTDOWN_PORT% -javaagent:jolokia-agent.jar=host=0.0.0.0,port=%JOLOKIA_PORT%,user=%TOMCAT_USERNAME%,password=%TOMCAT_PASSWORD%
) else (
  set CATALINA_OPTS=%CATALINA_OPTS% -DhttpPort=%HTTP_PORT% -DshutdownPort=%SHUTDOWN_PORT% -javaagent:jolokia-agent.jar=host=0.0.0.0,port=%JOLOKIA_PORT%,user=%TOMCAT_USERNAME%,password=%TOMCAT_PASSWORD%
)

