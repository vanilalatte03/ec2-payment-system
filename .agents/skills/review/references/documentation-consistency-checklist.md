# Documentation Consistency Checklist

코드가 문서와 맞는지 확인한다.

## README

* 프로젝트 범위와 실제 구현 범위가 일치하는가
* 실행 방법이 현재 코드와 맞는가
* 환경변수 설명이 누락되지 않았는가
* 주요 기능 설명과 실제 API가 충돌하지 않는가

## API

* Method 일치 여부
* Endpoint 경로 일치 여부
* Request Body 일치 여부
* Response Body 일치 여부
* 상태 코드 일치 여부
* 에러 코드 일치 여부
* 인증 필요 여부 일치 여부

## ERD

* Entity명과 테이블명이 일치하는가
* 필드명이 일치하는가
* 컬럼 타입이 크게 어긋나지 않는가
* 관계가 일치하는가
* Enum 값이 일치하는가
* unique, nullable, FK 조건이 일치하는가
