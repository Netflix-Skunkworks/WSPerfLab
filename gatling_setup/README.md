Use ./install_gatling_all.py to install gatling on servers.

./install_gatling_all.py -s ssh -u <url_base_to_load_test> -r <hosts_to_install_on>

the parameter -r is a file of hosts to install on. (one host per line)

To run the gatlings on the local gatling server directly, cd ~WSPerfLab/gatling_setup/gatling_driver/run_gatling_locally.py <URL_base_to_test>

To get the reports:
cd ~WSPerfLab/gatling_setup/gatling_driver; bash build_report.sh <path_to_simulation.log>

Report output format is concurrency[TAB][success per second][TAB][failure per second][TAB][python list of latencies at percentiles]