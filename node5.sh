./run.sh Glubokoslav "$(ip addr show enp0s3 | awk '/inet / {print $2}' | cut -d '/' -f 1)" 6090