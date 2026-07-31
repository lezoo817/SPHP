"""挂号工具封装（系分 §5.3）。

MCP 工具：query_departments, query_doctors, query_schedule_slots,
          create_appointment, query_appointments, cancel_appointment,
          join_waitlist, query_payment_status
对应 Java API: /api/c/v1/departments, /doctors, /appointments 等
"""

from app.infrastructure.java_client import call_java_api


async def query_departments(hospital_id: int, keyword: str | None = None, user_id: int | None = None) -> dict:
    """查询科室列表。"""
    params = {"hospital_id": hospital_id}
    if keyword:
        params["keyword"] = keyword
    return await call_java_api("GET", "/api/c/v1/departments", params=params, user_id=user_id, scope="c_end")


async def query_doctors(hospital_id: int, department_id: int, date: str | None = None, user_id: int | None = None) -> dict:
    """按科室查询医生及号源概览。"""
    params = {"hospital_id": hospital_id, "department_id": department_id}
    if date:
        params["date"] = date
    return await call_java_api("GET", "/api/c/v1/doctors", params=params, user_id=user_id, scope="c_end")


async def query_schedule_slots(doctor_id: int, hospital_id: int, date: str, user_id: int | None = None) -> dict:
    """查询医生可预约时段与余量。"""
    params = {"hospital_id": hospital_id, "date": date}
    return await call_java_api("GET", f"/api/c/v1/doctors/{doctor_id}/slots", params=params, user_id=user_id, scope="c_end")


async def create_appointment(hospital_id: int, slot_id: int, patient_id: int | None = None, user_id: int | None = None) -> dict:
    """创建挂号锁定订单（预扣号源）。"""
    body = {"hospital_id": hospital_id, "slot_id": slot_id}
    if patient_id:
        body["patient_id"] = patient_id
    return await call_java_api("POST", "/api/c/v1/appointments", body=body, user_id=user_id, scope="c_end")


async def query_appointments(appointment_id: int | None = None, status: str | None = None, user_id: int | None = None) -> dict:
    """查询挂号订单列表或详情。"""
    if appointment_id:
        return await call_java_api("GET", f"/api/c/v1/appointments/{appointment_id}", user_id=user_id, scope="c_end")
    params = {}
    if status:
        params["status"] = status
    return await call_java_api("GET", "/api/c/v1/appointments", params=params, user_id=user_id, scope="c_end")


async def cancel_appointment(appointment_id: int, user_id: int | None = None) -> dict:
    """取消挂号锁定订单。"""
    return await call_java_api("POST", f"/api/c/v1/appointments/{appointment_id}/cancel", user_id=user_id, scope="c_end")


async def join_waitlist(slot_id: int, patient_id: int | None = None, user_id: int | None = None) -> dict:
    """号源约满时登记候补。"""
    body = {"slot_id": slot_id}
    if patient_id:
        body["patient_id"] = patient_id
    return await call_java_api("POST", "/api/c/v1/waitlists", body=body, user_id=user_id, scope="c_end")


async def query_payment_status(payment_id: int, user_id: int | None = None) -> dict:
    """查询支付单状态。"""
    return await call_java_api("GET", f"/api/c/v1/payments/{payment_id}", user_id=user_id, scope="c_end")


def register(server):
    """注册工具到 MCP Server。"""
    pass
