-- Una base por servicio, dentro del mismo Postgres. Cada servicio solo conoce el nombre de la suya
-- (DB_NAME en docker-compose.yml) y nadie hace JOIN contra la base de otro.
-- Postgres corre este archivo UNA vez, la primera vez que arranca con el volumen vacio.
-- En produccion serian instancias separadas; aqui comparten contenedor para que la demo quepa en una laptop.
CREATE DATABASE cuenta;
CREATE DATABASE movimiento;
CREATE DATABASE transferencia;
CREATE DATABASE notificacion;
CREATE DATABASE spei;
