# Gatherly - A Local Event Board
A community platform where users can browse, post, and RSVP to local events.


## Key Features
- Browse and search events pulled from a headless CMS (`Sanity`).
- User authentication via `Supabase Auth` with protected RSVP endpoints.
- Image/file uploads via `Cloudinary`.
- RSVP system with `Spring Boot API` and `PostgreSQL`.
- Soft delete for events with a periodically run `CRON` job to purge old records.
- Paginated and filtered API endpoints.
- Test coverage with `JUnit` and `@SpringBootTest`
- `SpringDoc` for API documentation and `Swagger UI`.

## Basic Workflow
1. User registers/logs in
   - Supabase Auth issues JWT
   - User record saved in PostgreSQL
2. Organizer creates event in Sanity
   - Sanity stores content (title, description, tags, images, location)
   - Spring Boot syncs or fetches event reference → saved in PostgreSQL
3. General user browses events
   - Request hits Spring Boot API
   - Spring Boot fetches content from Sanity + joins with PostgreSQL data
   - Returns unified event response
4. General user RSVPs
   - Request hits protected Spring Boot endpoint (JWT validated)
   - RSVP record written to PostgreSQL
   - Event record updated with new RSVP count
