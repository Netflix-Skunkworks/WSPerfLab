LATEST_LOG='latest_simulation.log'
rm -f $LATEST_LOG
cd /apps/stresstest/gatling-maven-plugin-demo
mvn gatling:execute -Dgatling.simulationClass=netflix.LoadDriver
CURRENT_LOG=`find /apps/stresstest/gatling-maven-plugin-demo/ -name simulation.log -print | sort -n | tail -1`
cd $HOME
ln -s $CURRENT_LOG $LATEST_LOG