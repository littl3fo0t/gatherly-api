# Gatherly — A Local Event Board
A full stack community event platform where users can discover local events, create and manage their own, and RSVP for ones they want to attend.

## Technologies

### Backend:
 - Java & Spring Boot
 - PostgreSQL
 - Supabase Auth (JWT-based authentication)
 - Cloudinary (image uploads)
 - SpringDoc & Swagger UI (API documentation)
 - JUnit 5 & Mockito (testing)

### Frontend:
 - Next.js
 - Tailwind CSS
 - shadcn/ui
 - TipTap (rich text editing)

### Deployment:
 - Render (API & database)
 - Vercel (frontend)

## Basic Workflow (WIP)
1. User registers/logs in
   - Supabase Auth issues JWT
   - User record saved in PostgreSQL
2. Organizer creates event in ~~Sanity~~ UI
   - UI makes `POST` request to `Supabase`
   - ~~Sanity~~ `Supabase` stores content (title, description, tags, images, location, etc...)
   - ~~Spring Boot syncs or fetches event reference → saved in PostgreSQL~~
4. General user browses events
   - Request hits Spring Boot API
   - Spring Boot fetches content from ~~Sanity + joins with PostgreSQL data~~ `Supabase`
   - Returns ~~unified~~ event response
5. General user RSVPs
   - Request hits protected Spring Boot endpoint (JWT validated)
   - RSVP record written to PostgreSQL
   - Event record updated with new RSVP count
