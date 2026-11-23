.PHONY: hdfs-up

hdfs-up: check-local-environment
	docker compose --profile hdfs up -d --wait hdfs-namenode hdfs-datanode hdfs-httpfs

ifeq ($(HDFS_ENABLED),true)
dev: hdfs-up
recommendations-dev: hdfs-up
endif
