import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UserService } from './user.service';
import { UserProfile, UpdateProfileRequest } from '../../../core/models/user-profile.model';

const BASE_URL = 'http://localhost:8080/api/v1/users';

const mockProfile: UserProfile = {
  id: '11111111-1111-1111-1111-111111111111',
  tenantId: '22222222-2222-2222-2222-222222222222',
  email: 'jane.doe@example.com',
  firstName: 'Jane',
  lastName: 'Doe',
  bio: 'Software engineer',
  avatarUrl: null,
  version: 1,
};

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        UserService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getMe()', () => {
    it('should call GET /api/v1/users/me and return UserProfile', () => {
      let result: UserProfile | undefined;
      service.getMe().subscribe((r) => (result = r));

      const req = httpMock.expectOne(`${BASE_URL}/me`);
      expect(req.request.method).toBe('GET');
      req.flush(mockProfile);

      expect(result).toEqual(mockProfile);
      expect(result!.email).toBe('jane.doe@example.com');
    });
  });

  describe('updateMe()', () => {
    it('should call PATCH /api/v1/users/me with UpdateProfileRequest body and return updated UserProfile', () => {
      const updateReq: UpdateProfileRequest = {
        firstName: 'Janet',
        bio: 'Updated bio',
        version: 1,
      };
      const updatedProfile: UserProfile = { ...mockProfile, firstName: 'Janet', bio: 'Updated bio', version: 2 };

      let result: UserProfile | undefined;
      service.updateMe(updateReq).subscribe((r) => (result = r));

      const req = httpMock.expectOne(`${BASE_URL}/me`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual(updateReq);
      req.flush(updatedProfile);

      expect(result).toEqual(updatedProfile);
      expect(result!.version).toBe(2);
    });

    it('should include version in PATCH body (optimistic lock)', () => {
      const updateReq: UpdateProfileRequest = { version: 3 };
      service.updateMe(updateReq).subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/me`);
      expect(req.request.body).toMatchObject({ version: 3 });
      req.flush({ ...mockProfile, version: 4 });
    });
  });
});
