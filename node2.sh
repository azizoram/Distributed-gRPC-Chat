./run.sh ChRamir "$(ip addr show enp0s3 | awk '/inet / {print $2}' | cut -d '/' -f 1)" 3030