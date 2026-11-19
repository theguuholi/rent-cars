#!/usr/bin/env bash
set -euo pipefail
export HADOOP_CONF_DIR=/tmp/test-conf
export HADOOP_LOG_DIR=/tmp/test-logs
export HADOOP_HEAPSIZE_MAX=256
mkdir -p "$HADOOP_CONF_DIR" "$HADOOP_LOG_DIR" /tmp/hdfs/name /tmp/hdfs/blocks
cp -r /opt/hadoop/etc/hadoop/* "$HADOOP_CONF_DIR/"
cat > "$HADOOP_CONF_DIR/core-site.xml" <<'XML'
<configuration>
<property><name>fs.defaultFS</name><value>hdfs://localhost:9000</value></property>
<property><name>hadoop.proxyuser.hadoop.hosts</name><value>*</value></property>
<property><name>hadoop.proxyuser.hadoop.groups</name><value>*</value></property>
</configuration>
XML
cat > "$HADOOP_CONF_DIR/hdfs-site.xml" <<'XML'
<configuration>
<property><name>dfs.namenode.name.dir</name><value>file:///tmp/hdfs/name</value></property>
<property><name>dfs.datanode.data.dir</name><value>file:///tmp/hdfs/blocks</value></property>
<property><name>dfs.replication</name><value>1</value></property>
<property><name>dfs.permissions.enabled</name><value>false</value></property>
<property><name>dfs.namenode.safemode.extension</name><value>0</value></property>
</configuration>
XML
cat > "$HADOOP_CONF_DIR/httpfs-site.xml" <<'XML'
<configuration>
<property><name>httpfs.hadoop.config.dir</name><value>/tmp/test-conf</value></property>
<property><name>httpfs.authentication.type</name><value>simple</value></property>
</configuration>
XML
if [[ ! -f /tmp/hdfs/name/current/VERSION ]]; then hdfs namenode -format -nonInteractive; fi
hdfs --daemon start namenode
hdfs --daemon start datanode
exec hdfs httpfs
