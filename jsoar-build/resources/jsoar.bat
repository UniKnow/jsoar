@echo off
rem This is a startup script for the JSoar debugger. It's mostly "for fun"
rem because JSoar is typically used as a library. The debugger that is
rem started can be used for running simple agents with no I/O.
rem
rem Usage:
rem
rem > jsoar [soar files or URLs] 

set HERE=%~sp0

rem Enable Legilimens web interface just for fun
set JSOAR_OPTS=-Xmx1024m -Djsoar.legilimens.autoStart=true

java %JSOAR_OPTS% -cp "%HERE%lib\*" org.jsoar.debugger.JSoarDebugger %*
