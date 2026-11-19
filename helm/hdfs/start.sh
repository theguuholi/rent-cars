#!/usr/bin/env bash
# Local learning cluster only: pseudo-authentication, no TLS or high availability.
set -euo pipefail
# Compose named volumes start root-owned; drop privileges before running Hadoop.
if [[ $(id -u) == 0 ]]; then
  mkdir -p /data/name /data/blocks
  chown -R 1001:100 /data
  exec runuser -u hadoop -- bash "$0" "$@"
fi
export HADOOP_CONF_DIR=/tmp/rent-hadoop-conf
export HADOOP_LOG_DIR=/tmp/hadoop-logs
export HADOOP_HEAPSIZE_MAX=256
mkdir -p "$HADOOP_CONF_DIR" "$HADOOP_LOG_DIR" /data/name /data/blocks
cp -r /opt/hadoop/etc/hadoop/* "$HADOOP_CONF_DIR/"
cat > "$HADOOP_CONF_DIR/core-site.xml" <<XML
<configuration>
  <property><name>fs.defaultFS</name><value>hdfs://${HDFS_NAMENODE_HOST:-hdfs-namenode}:9000</value></property>
  <property><name>hadoop.proxyuser.hadoop.hosts</name><value>*</value></property>
  <property><name>hadoop.proxyuser.hadoop.groups</name><value>*</value></property>
</configuration>
XML
cat > "$HADOOP_CONF_DIR/hdfs-site.xml" <<'XML'
<configuration>
  <property><name>dfs.namenode.name.dir</name><value>file:///data/name</value></property>
  <property><name>dfs.datanode.data.dir</name><value>file:///data/blocks</value></property>
  <property><name>dfs.replication</name><value>1</value></property>
  <property><name>dfs.permissions.enabled</name><value>false</value></property>
  <property><name>dfs.namenode.datanode.registration.ip-hostname-check</name><value>false</value></property>
  <property><name>dfs.namenode.safemode.extension</name><value>0</value></property>
</configuration>
XML
cat > "$HADOOP_CONF_DIR/httpfs-site.xml" <<XML
<configuration>
  <property><name>httpfs.hadoop.config.dir</name><value>${HADOOP_CONF_DIR}</value></property>
  <property><name>httpfs.authentication.type</name><value>simple</value></property>
</configuration>
XML
case "$1" in
  namenode)
    if [[ ! -f /data/name/current/VERSION ]]; then hdfs namenode -format -nonInteractive; fi
    exec hdfs namenode
    ;;
  datanode) exec hdfs datanode ;;
  httpfs) exec hdfs httpfs ;;
  *) echo 'Expected namenode, datanode or httpfs' >&2; exit 1 ;;
esac
