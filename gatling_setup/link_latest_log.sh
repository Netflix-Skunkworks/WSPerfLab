LATEST_LOG='latest_simulation.log'
rm -f $LATEST_LOG
CURRENT_LOG=`find /apps/stresstest/gatling-maven-plugin-demo/ -name simulation.log -print | sort -n | tail -1`
ln -s $CURRENT_LOG $LATEST_LOG