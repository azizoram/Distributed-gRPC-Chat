#!/bin/bash

# Set the URL to the zip file
download_url="https://gitlab.fel.cvut.cz/azizoram/grpc_chat_dsv/-/archive/master/grpc_chat_dsv-master.zip"

# Set the name of the zip file
zip_file="grpc_chat_dsv-master.zip"

# Set the directory to extract the contents
extract_dir="grpc_chat_dsv-master"

# Download the zip file
wget "$download_url" -O "$zip_file"

# Unzip the downloaded file
unzip "$zip_file"

# Navigate into the extracted directory
cd "$extract_dir"

# Navigate into the grpc_chat_dsv directory
cd grpc_chat_dsv

# Launch the build script
./build.sh