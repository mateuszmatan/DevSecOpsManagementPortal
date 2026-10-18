export const PRODUCT_CODE = /^[A-Z][A-Z0-9_-]{1,49}$/;

export interface FieldProblem {
  field: string;
  message: string;
}

export interface BusinessService {
  id: number;
  name: string;
  version: number;
  productCount: number;
}

export type Department = BusinessService;

export interface BusinessServiceRequest {
  name: string;
  version: number | null;
}

export type DepartmentRequest = BusinessServiceRequest;

export interface LookupItem {
  value: string;
  detail: string | null;
}
