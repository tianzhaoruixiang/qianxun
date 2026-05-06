#!/bin/bash
# script to quick install KnowledgeBaseManageService

APP_NAME=QianXunService

curDir=$(cd `dirname $0`;pwd)
cd $curDir

INSTALLDIR=/work/bin/${APP_NAME}

mkdir -p $INSTALLDIR
echo "create directory success"

cp -rf ./* $INSTALLDIR/

echo "end to install KnowledgeBaseManageService"
echo "---------------------------------------------------------"
