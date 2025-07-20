## execute T2T stored procedures

```bash
./run.sh exec-proc \
-d ECICMD03_svc01 \
-u MAV_T2T_APP \
"MAV_OWNER.TempTable_Onehadoop_proc" \
--input "in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:136,in_prant_grp:VARCHAR2:HADOOP_DML_ROLE" \
--output "p_outmsg:STRING"
```

```bash
./run.sh exec-proc \
-d ECICMD03_svc01 \
-u MAV_T2T_APP \
--vault-url "https://vault.example.com" \
--role-id "your-role-id" \
--secret-id "your-secret-id" \
--ait "your-ait"
"MAV_OWNER.TempTable_Onehadoop_proc" \
--input "in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:136,in_prant_grp:VARCHAR2:HADOOP_DML_ROLE" \
--output "p_outmsg:STRING"
```


## Vault client CLI 

```bash
./run.sh exec-vault lookup \
-d ECICMD03_svc01 \
-u MAV_T2T_APP
```

```bash
./run.sh exec-vault \
-d ECICMD03_svc01 \
-u MAV_T2T_APP \
--vault-url "https://vault.example.com" \
--role-id "your-role-id" \
--secret-id "your-secret-id" \
--ait "your-ait"
```

## execute sql statement

```bash
./run.sh exec-sql "SELECT * FROM employees WHERE department = 'IT'" \
-d ECICMD03_svc01 \
-u MAV_T2T_APP

./run.sh exec-sql "SELECT * FROM employees WHERE department = ? AND salary > ?" \
-d ECICMD03_svc01 \
-u MAV_T2T_APP \
--params "IT,50000"
```

## execute sql script

```bash
./run.sh exec-sql \
-d ECICMD03_svc01 \
-u MAV_T2T_APP \
--script "/path/to/script.sql"
```
