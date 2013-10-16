Use ./install_gatling_all.py to install gatling on servers.

./install_gatling_all.py -s ssh -u <url_base_to_load_test> -r <hosts_to_install_on>

the parameter -r is a file of hosts to install on. (one host per line)

Note that the program will also run the gatlings concurrently.  Logs are in the logs.sh directory.   Since the first gatling run will also download artifacts from Maven, its suggested to discard the first run and then pay attention to the second run.

Getting the reports:
  After you run the gatling, copy the simulation logs to your local directory by doing cd gatling_driver/get_reports.py script