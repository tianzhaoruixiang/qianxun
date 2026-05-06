#!/bin/sh
# author: ww
# update date: 20190613

APP_NAME=QianXunService

SERVICE_PATH=/work/bin/${APP_NAME}/
JAR_FILE=/work/bin/${APP_NAME}/${APP_NAME}.jar
cp /work/bin/${APP_NAME}/lib/*.jar /work/lib/
rm -f /work/lib/repository/org/slf4j/log4j-over-slf4j/2.0.11/log4j-over-slf4j-2.0.11.jar
rm -f /work/lib/repository/org/apache/logging/log4j/log4j-api/2.6.2/log4j-api-2.6.2.jar
rm -f /work/lib/repository/org/apache/logging/log4j/log4j-core/2.6.2/log4j-core-2.6.2.jar
rm -f /work/lib/repository/org/apache/logging/log4j/log4j-to-slf4j/2.14.1/log4j-to-slf4j-2.14.1.jar
rm -f /work/lib/log4j-over-slf4j-2.0.11.jar
rm -f /work/lib/log4j-api-2.6.2.jar
rm -f /work/lib/log4j-core-2.6.2.jar
rm -f /work/lib/log4j-to-slf4j-2.14.1.jar

cd $SERVICE_PATH
tpid=`ps -ef | grep ${APP_NAME}".jar" | grep -v  grep | grep -v kill | awk '{print $2}'`
if [ ${tpid} ]; then
    echo "App is running!"
else
    java -jar -XX:+UseZGC -XX:+ZGenerational -XX:+UseContainerSupport -XX:MaxRAMPercentage=80.0 $JAR_FILE --spring.profiles.active=prod $1
    echo $! > tpid
    echo "Start ${APP_NAME} Success!"
fi