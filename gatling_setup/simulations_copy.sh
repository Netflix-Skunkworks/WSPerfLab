mkdir -p downloads
chmod 777 downloads
cd downloads
oq-scp %(instance_id)s:latest_simulation.log %(instance_id)s.log