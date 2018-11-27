#!/bin/sh
set -e

mkdir -p /etc/cfssl

if [ ! -f /etc/cfssl/ca-config.json ];
then 
  # CA configuration file is not present, generate the default.
  echo "Generating default CA configuration file: /etc/cfssl/ca-config.json"

  cat <<- EOF > /etc/cfssl/ca-config.json 
{
    "signing": {
        "default": {
            "expiry": "26280h",
            "usages": [
                "signing",
                "key encipherment",
                "client auth",
                "server auth"
            ]
        },
        "profiles": {
            "server": {
                "expiry": "26280h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "server auth"
                ]
            },
            "client": {
                "expiry": "26280h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "client auth"
                ]
            },
            "peer": {
                "expiry": "26280h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "server auth",
                    "client auth"
                ]
            }
        }
    }
}
EOF
fi 

if [ ! -f /etc/cfssl/ca-csr.json ];
then 
  # CA certificate signing request file is not present, generate the default.
  echo "Generating default CA certificate signing request file: /etc/cfssl/ca-csr.json"

  cat <<- EOF > /etc/cfssl/ca-csr.json 
{
    "CN": "blackducksoftware",
    "key": {
        "algo": "rsa",
        "size": 2048
    },
    "names": [
        {
            "C": "US",
            "ST": "Massachusetts",
            "L": "Burlington",
            "O": "Black Duck Software, Inc.",
            "OU": "Engineering"
        }
    ],
    "ca": {
        "expiry": "26280h"
    }
}
EOF
fi

if [ ! -f /etc/cfssl/ca-key.pem ] || [ ! -f /etc/cfssl/ca.pem ];
then 
  echo "Attempting to generate CA files." 
  cfssl gencert -initca /etc/cfssl/ca-csr.json | cfssljson -bare ca

  presentWorkingDirectory=$(pwd)
  if [ "$presentWorkingDirectory" != "/etc/cfssl" ];
  then 
    mv ca-key.pem /etc/cfssl/ca-key.pem
    mv ca.pem /etc/cfssl/ca.pem
    mv ca.csr /etc/cfssl/ca.csr
  fi 

  chmod 440 /etc/cfssl/ca-key.pem  
  chmod 644 /etc/cfssl/ca.pem /etc/cfssl/ca.csr
fi

if [ "$(id -u)" = '0' ]; then
    set -- su-exec cfssl:root "$@"
fi
exec "$@" cfssl serve -ca-key=/etc/cfssl/ca-key.pem -ca=/etc/cfssl/ca.pem -config=/etc/cfssl/ca-config.json -address=0.0.0.0 -port=8888