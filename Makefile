.PHONY: infra infra-down backend frontend test build clean

infra:
	docker compose -f vector-database.yml up -d

infra-down:
	docker compose -f vector-database.yml down

backend:
	mvn spring-boot:run

frontend:
	cd frontend && npm install && npm run dev

test:
	mvn test

build:
	mvn clean package
	cd frontend && npm install && npm run build

clean:
	mvn clean
	rm -rf frontend/dist
