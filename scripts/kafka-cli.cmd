@echo off
REM Handy Kafka CLI wrappers for exploring the platform topics on localhost:9092.
REM   kafka-cli topics                       list topics
REM   kafka-cli describe <topic>             partitions / leaders
REM   kafka-cli groups                       consumer groups with lag (policy-service, notification-service)
REM   kafka-cli tail <topic>                 print records (key | value) from the beginning, Ctrl+C to stop
REM   kafka-cli dlt                          records parked on the dead-letter topics
REM   kafka-cli produce <topic> [key]        type "key:value" lines; Ctrl+C to stop
setlocal
if "%KAFKA_HOME%"=="" set KAFKA_HOME=C:\Softwares\kafka\kafka_2.13-4.3.1
if "%KAFKA_BOOTSTRAP_SERVERS%"=="" set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set BIN=%KAFKA_HOME%\bin\windows
if "%1"=="topics"   call "%BIN%\kafka-topics.bat" --bootstrap-server %KAFKA_BOOTSTRAP_SERVERS% --list & goto :eof
if "%1"=="describe" call "%BIN%\kafka-topics.bat" --bootstrap-server %KAFKA_BOOTSTRAP_SERVERS% --describe --topic %2 & goto :eof
if "%1"=="groups"   call "%BIN%\kafka-consumer-groups.bat" --bootstrap-server %KAFKA_BOOTSTRAP_SERVERS% --describe --all-groups & goto :eof
if "%1"=="tail"     call "%BIN%\kafka-console-consumer.bat" --bootstrap-server %KAFKA_BOOTSTRAP_SERVERS% --topic %2 --from-beginning --property print.key=true --property print.headers=true --property key.separator=" | " & goto :eof
if "%1"=="dlt"      call "%BIN%\kafka-console-consumer.bat" --bootstrap-server %KAFKA_BOOTSTRAP_SERVERS% --include "payment.succeeded.DLT|notification.requested.DLT" --from-beginning --property print.key=true --property print.headers=true --timeout-ms 5000 & goto :eof
if "%1"=="produce"  call "%BIN%\kafka-console-producer.bat" --bootstrap-server %KAFKA_BOOTSTRAP_SERVERS% --topic %2 --property parse.key=true --property key.separator=: & goto :eof
echo usage: kafka-cli topics ^| describe ^<topic^> ^| groups ^| tail ^<topic^> ^| dlt ^| produce ^<topic^>
